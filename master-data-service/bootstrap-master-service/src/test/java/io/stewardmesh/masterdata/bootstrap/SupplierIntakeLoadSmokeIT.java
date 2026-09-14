package io.stewardmesh.masterdata.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/**
 * Bounded load smoke for the intake to golden-record pipeline.
 *
 * <p>Every other proof drives one request at a time, so nothing exercises what concurrency actually
 * threatens: a lost update between parallel transactions, a duplicated effect when the same
 * idempotency key races itself, or a latency cliff once requests contend for the pool. This test
 * drives the deployed stack over real HTTP with real signed tokens, because a load smoke that
 * bypasses the servlet container and the security filter chain measures something the deployment
 * never runs.
 *
 * <p>It is deliberately a smoke rather than a benchmark. The thresholds are generous ceilings that
 * catch a collapse, not performance targets, so the gate stays meaningful on slower CI hardware.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "stewardmesh.messaging.enabled=false")
@Import(SupplierIntakeLoadSmokeIT.S3TestConfiguration.class)
class SupplierIntakeLoadSmokeIT {

    private static final String BUCKET = "stewardmesh-intake-load";
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String AUDIENCE = "stewardmesh-master-data";
    private static final String BOUNDARY = "stewardmesh-load-smoke-boundary";

    /** Enough parallelism to contend for the pool without turning CI into a benchmark. */
    private static final int CONCURRENT_TENANTS = 12;
    private static final int CONCURRENT_REPLAYS = 8;
    private static final Duration WORST_CASE_IMPORT = Duration.ofSeconds(20);
    private static final Duration WORST_CASE_BATCH = Duration.ofSeconds(60);
    private static final int BUCKET_ATTEMPTS = 10;

    private static final RSAKey SIGNING_KEY = generateKey();
    private static final HttpServer JWK_SERVER = startJwkServer();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final GenericContainer<?> LOCALSTACK = new GenericContainer<>(
                    DockerImageName.parse("localstack/localstack:4.14.0"))
            .withEnv("SERVICES", "s3")
            .withExposedPorts(4566)
            .waitingFor(Wait.forHttp("/_localstack/health").forStatusCode(200));

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("stewardmesh.intake-storage.endpoint", () -> endpoint().toString());
        registry.add("stewardmesh.intake-storage.bucket", () -> BUCKET);
        registry.add("stewardmesh.intake-storage.region", () -> Region.US_EAST_1.id());
        registry.add("stewardmesh.intake-storage.force-path-style", () -> true);
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWK_SERVER.getAddress().getPort() + "/jwks");
        registry.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> AUDIENCE);
    }

    @AfterAll
    static void stopJwkServer() {
        JWK_SERVER.stop(0);
    }

    @Test
    void sustainsConcurrentDistinctImportsWithoutLosingOrDuplicatingAnEffect() throws Exception {
        List<Upload> uploads = new ArrayList<>();
        for (int tenant = 1; tenant <= CONCURRENT_TENANTS; tenant++) {
            uploads.add(new Upload(
                    "LOAD_TENANT_" + tenant,
                    "load-" + tenant,
                    SyntheticSupplierWorkbooks.forTenant(tenant)));
        }

        Instant startedAt = Instant.now();
        List<Outcome> outcomes = runConcurrently(uploads);
        Duration batch = Duration.between(startedAt, Instant.now());

        assertEquals(
                Set.of(202),
                outcomes.stream().map(Outcome::status).collect(Collectors.toUnmodifiableSet()),
                () -> "unexpected statuses " + statusHistogram(outcomes));
        assertEquals(
                Set.of("MATCHED"),
                outcomes.stream().map(Outcome::importStatus).collect(Collectors.toUnmodifiableSet()));
        assertEquals(
                CONCURRENT_TENANTS,
                outcomes.stream().map(Outcome::importId).distinct().count(),
                "every distinct tenant must own a separate import");
        assertTrue(
                outcomes.stream().noneMatch(Outcome::replayed),
                "distinct tenants must not be mistaken for replays of one another");

        // Three rows per tenant, of which two describe one party with two sites.
        assertEquals(CONCURRENT_TENANTS, count("SELECT COUNT(*) FROM import_job"));
        assertEquals(CONCURRENT_TENANTS * 3, count("SELECT COUNT(*) FROM source_record"));
        assertEquals(
                CONCURRENT_TENANTS * 2,
                count("SELECT COUNT(*) FROM golden_record_metadata WHERE entity_type = 'PARTY'"),
                "each tenant contributes exactly two parties and none may be merged or lost");
        assertEquals(
                count("SELECT COUNT(*) FROM golden_record_metadata"),
                count("SELECT COUNT(DISTINCT (entity_type, entity_id)) FROM golden_record_metadata"),
                "a lost update would leave a duplicated projection identity");

        assertTrue(
                batch.compareTo(WORST_CASE_BATCH) < 0,
                () -> CONCURRENT_TENANTS + " concurrent imports took " + batch);
        Duration worst = outcomes.stream()
                .map(Outcome::elapsed)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        assertTrue(
                worst.compareTo(WORST_CASE_IMPORT) < 0,
                () -> "slowest concurrent import took " + worst);
    }

    @Test
    void collapsesAConcurrentIdempotentReplayIntoOneImport() throws Exception {
        byte[] workbook = SyntheticSupplierWorkbooks.forTenant(99);
        String sourceSystem = "LOAD_REPLAY";
        String key = "load-replay-1";
        List<Upload> identical = IntStream.range(0, CONCURRENT_REPLAYS)
                .mapToObj(attempt -> new Upload(sourceSystem, key, workbook))
                .toList();

        List<Outcome> outcomes = runConcurrently(identical);

        int accepted = count(
                "SELECT COUNT(*) FROM import_job WHERE source_system = ?", sourceSystem);
        assertEquals(1, accepted, "a raced idempotency key must never create a second import");
        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM idempotency_record WHERE source_system = ?",
                        sourceSystem));
        assertEquals(
                3,
                count(
                        """
                        SELECT COUNT(*) FROM source_record
                        WHERE import_job_id IN (SELECT id FROM import_job WHERE source_system = ?)
                        """,
                        sourceSystem),
                "the workbook rows must be stored exactly once");

        List<Outcome> succeeded =
                outcomes.stream().filter(outcome -> outcome.status() == 202).toList();
        assertTrue(!succeeded.isEmpty(), () -> "no attempt succeeded " + statusHistogram(outcomes));
        assertEquals(
                1,
                succeeded.stream().map(Outcome::importId).distinct().count(),
                "every accepted attempt must name the same import");
        assertEquals(
                Map.of(202, (long) CONCURRENT_REPLAYS),
                statusHistogram(outcomes),
                "a raced idempotency key is a replay, not a client conflict");
        assertEquals(
                1,
                outcomes.stream().filter(outcome -> !outcome.replayed()).count(),
                "exactly one attempt may report itself as the original import");
    }

    private List<Outcome> runConcurrently(List<Upload> uploads) throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Outcome>> calls = uploads.stream()
                    .map(upload -> (Callable<Outcome>) () -> send(upload))
                    .toList();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> completed : executor.invokeAll(calls)) {
                outcomes.add(completed.get());
            }
            return outcomes;
        }
    }

    private Outcome send(Upload upload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/v1/supplier-imports"))
                .header("Authorization", "Bearer " + token("supplier-import.write"))
                .header("Idempotency-Key", upload.idempotencyKey())
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .timeout(WORST_CASE_IMPORT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(upload)))
                .build();

        Instant startedAt = Instant.now();
        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build()) {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }
        Duration elapsed = Duration.between(startedAt, Instant.now());

        String importId = null;
        String importStatus = null;
        boolean replayed = false;
        if (response.statusCode() == 202) {
            importId = JsonPath.read(response.body(), "$.importId");
            importStatus = JsonPath.read(response.body(), "$.status");
            replayed = JsonPath.read(response.body(), "$.replayed");
        }
        return new Outcome(response.statusCode(), importId, importStatus, replayed, elapsed);
    }

    private static byte[] multipart(Upload upload) throws IOException {
        var body = new ByteArrayOutputStream();
        body.write(part("sourceSystem", upload.sourceSystem()));
        body.write(
                ("--" + BOUNDARY + "\r\n"
                                + "Content-Disposition: form-data; name=\"workbook\";"
                                + " filename=\"synthetic.xlsx\"\r\n"
                                + "Content-Type: " + XLSX_CONTENT_TYPE + "\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
        body.write(upload.workbook());
        body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    private static byte[] part(String name, String value) {
        return ("--" + BOUNDARY + "\r\n"
                        + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                        + value + "\r\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static Map<Integer, Long> statusHistogram(List<Outcome> outcomes) {
        return outcomes.stream()
                .collect(Collectors.groupingBy(Outcome::status, Collectors.counting()));
    }

    private int count(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arguments);
    }

    private static String token(String scope) {
        try {
            var claims = new JWTClaimsSet.Builder()
                    .subject("synthetic-load-client")
                    .issuer("https://synthetic-issuer.invalid/realms/stewardmesh")
                    .audience(AUDIENCE)
                    .issueTime(Date.from(Instant.now().minusSeconds(30)))
                    .expirationTime(Date.from(Instant.now().plusSeconds(900)))
                    .claim("scope", scope)
                    .build();
            var jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .type(JOSEObjectType.JWT)
                            .keyID(SIGNING_KEY.getKeyID())
                            .build(),
                    claims);
            jwt.sign(new RSASSASigner(SIGNING_KEY));
            return jwt.serialize();
        } catch (Exception failure) {
            throw new IllegalStateException("could not mint a synthetic token", failure);
        }
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("synthetic-load-key").generate();
        } catch (Exception failure) {
            throw new IllegalStateException("could not generate a synthetic signing key", failure);
        }
    }

    private static HttpServer startJwkServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] jwks = new JWKSet(List.of(SIGNING_KEY.toPublicJWK()))
                    .toString()
                    .getBytes(StandardCharsets.UTF_8);
            server.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                try (OutputStream response = exchange.getResponseBody()) {
                    response.write(jwks);
                }
            });
            server.start();
            return server;
        } catch (IOException failure) {
            throw new IllegalStateException("could not start the synthetic JWK server", failure);
        }
    }

    /**
     * LocalStack answers its health endpoint before S3 finishes starting, so creation is retried
     * briefly. The last attempt is allowed to fail loudly: a swallowed failure here would surface
     * later as every upload returning 500, which says nothing about the real cause.
     */
    @BeforeAll
    static void createBucket() throws InterruptedException {
        var request = CreateBucketRequest.builder().bucket(BUCKET).build();
        for (int attempt = 1; ; attempt++) {
            try (S3Client client = s3Client()) {
                client.createBucket(request);
                return;
            } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException existing) {
                return;
            } catch (RuntimeException notReadyYet) {
                if (attempt == BUCKET_ATTEMPTS) {
                    throw notReadyYet;
                }
                Thread.sleep(Duration.ofSeconds(1));
            }
        }
    }

    private static URI endpoint() {
        return URI.create("http://" + LOCALSTACK.getHost() + ':' + LOCALSTACK.getMappedPort(4566));
    }

    private static S3Client s3Client() {
        return S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(endpoint())
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .httpClientBuilder(ApacheHttpClient.builder())
                .build();
    }

    private record Upload(String sourceSystem, String idempotencyKey, byte[] workbook) {}

    private record Outcome(
            int status, String importId, String importStatus, boolean replayed, Duration elapsed) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class S3TestConfiguration {

        @Bean
        @Primary
        S3Client loadSmokeS3Client() {
            return s3Client();
        }
    }
}
