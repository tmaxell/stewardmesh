package io.stewardmesh.masterdata.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
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
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(SupplierIntakeEndToEndIT.S3TestConfiguration.class)
class SupplierIntakeEndToEndIT {

    private static final String BUCKET = "stewardmesh-intake-e2e";
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final GenericContainer<?> LOCALSTACK = new GenericContainer<>(
                    DockerImageName.parse("localstack/localstack:4.14.0"))
            .withEnv("SERVICES", "s3")
            .withExposedPorts(4566)
            .waitingFor(Wait.forHttp("/_localstack/health").forStatusCode(200));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private S3Client s3;

    @BeforeAll
    static void createBucket() {
        try (S3Client client = s3Client()) {
            client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        }
    }

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("stewardmesh.intake-storage.endpoint", () -> endpoint().toString());
        registry.add("stewardmesh.intake-storage.bucket", () -> BUCKET);
        registry.add("stewardmesh.intake-storage.region", () -> Region.US_EAST_1.id());
        registry.add("stewardmesh.intake-storage.force-path-style", () -> true);
    }

    @Test
    void validatesStoresAndReplaysOneWorkbookThroughThePublicApi() throws Exception {
        byte[] workbook = fixture("supplier-workbook-v1-valid.xlsx");
        long startedAt = System.nanoTime();

        MvcResult first = mockMvc.perform(upload("E2E_VALID", "valid-1", workbook).with(writeJwt()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.replayed").value(false))
                .andReturn();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
        String importId = JsonPath.read(first.getResponse().getContentAsString(), "$.importId");

        mockMvc.perform(upload("E2E_VALID", "valid-1", workbook).with(writeJwt()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.importId").value(importId))
                .andExpect(jsonPath("$.replayed").value(true));
        mockMvc.perform(get("/api/v1/supplier-imports/{id}", importId).with(readJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.counters.totalRows").value(3))
                .andExpect(jsonPath("$.counters.acceptedRows").value(3))
                .andExpect(jsonPath("$.counters.rejectedRows").value(0));
        mockMvc.perform(get("/api/v1/supplier-imports/{id}/report", importId).with(readJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIssues").value(0));

        assertTrue(elapsed.compareTo(Duration.ofSeconds(15)) < 0, () -> "intake took " + elapsed);
        assertEquals(1, count("SELECT COUNT(*) FROM import_job WHERE source_system = ?", "E2E_VALID"));
        assertEquals(3, count("SELECT COUNT(*) FROM source_record WHERE import_job_id = ?", uuid(importId)));
        String storageKey = jdbcTemplate.queryForObject(
                "SELECT a.storage_key FROM intake_artifact a JOIN import_job j ON j.artifact_id = a.id "
                        + "WHERE j.id = ?",
                String.class,
                uuid(importId));
        assertNotNull(s3.headObject(
                HeadObjectRequest.builder().bucket(BUCKET).key(storageKey).build()));
        assertEquals(1, s3.listObjectsV2(
                        ListObjectsV2Request.builder().bucket(BUCKET).prefix(storageKey).build())
                .keyCount());
        assertTrue(meterRegistry
                        .get("stewardmesh.imports.stage.duration")
                        .tag("stage", "artifact_store")
                        .timer()
                        .count()
                >= 1);
        assertTrue(meterRegistry
                        .get("stewardmesh.imports.stage.duration")
                        .tag("stage", "workbook_parse")
                        .timer()
                        .count()
                >= 1);
    }

    @Test
    void reportsMixedRowsWithDeterministicBoundedEvidence() throws Exception {
        MvcResult upload = mockMvc.perform(upload(
                                "E2E_MIXED",
                                "mixed-1",
                                fixture("supplier-workbook-v1-mixed-invalid.xlsx"))
                        .with(writeJwt()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andReturn();
        String importId = JsonPath.read(upload.getResponse().getContentAsString(), "$.importId");

        mockMvc.perform(get("/api/v1/supplier-imports/{id}", importId).with(readJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counters.totalRows").value(3))
                .andExpect(jsonPath("$.counters.acceptedRows").value(1))
                .andExpect(jsonPath("$.counters.rejectedRows").value(2))
                .andExpect(jsonPath("$.counters.warningCount").value(1))
                .andExpect(jsonPath("$.counters.errorCount").value(8));
        mockMvc.perform(get("/api/v1/supplier-imports/{id}/report?size=5", importId).with(readJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalIssues").value(9))
                .andExpect(jsonPath("$.issues.length()").value(5))
                .andExpect(jsonPath("$.issues[0].code").value("HEADER_UNKNOWN"))
                .andExpect(jsonPath("$.issues[1].code").value("REQUIRED_VALUE_MISSING"));
        assertTrue(meterRegistry
                        .get("stewardmesh.imports.validation.issues")
                        .tag("code", "header_unknown")
                        .counter()
                        .count()
                >= 1);
    }

    @Test
    void exposesARepeatedSourceIdentityAsARecoverablePersistenceFailure() throws Exception {
        byte[] workbook = fixture("supplier-workbook-v1-valid.xlsx");
        mockMvc.perform(upload("E2E_DUPLICATE", "duplicate-1", workbook).with(writeJwt()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("VALIDATED"));

        MvcResult duplicate = mockMvc.perform(
                        upload("E2E_DUPLICATE", "duplicate-2", workbook).with(writeJwt()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andReturn();
        String importId = JsonPath.read(duplicate.getResponse().getContentAsString(), "$.importId");

        mockMvc.perform(get("/api/v1/supplier-imports/{id}", importId).with(readJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("SOURCE_RECORD_PERSISTENCE_FAILED"));
        assertEquals(
                3,
                count("SELECT COUNT(*) FROM source_record WHERE origin_system = ?", "E2E_DUPLICATE"));
        assertEquals(2, count("SELECT COUNT(*) FROM import_job WHERE source_system = ?", "E2E_DUPLICATE"));
        assertTrue(meterRegistry
                        .get("stewardmesh.imports.stage.failures")
                        .tag("stage", "persistence_batch")
                        .counter()
                        .count()
                >= 1);
    }

    @Test
    void rejectsCorruptContentBeforeCreatingAnyState() throws Exception {
        int objectsBefore = s3.listObjectsV2(
                        ListObjectsV2Request.builder().bucket(BUCKET).build())
                .keyCount();
        mockMvc.perform(upload("E2E_CORRUPT", "corrupt-1", new byte[] {1, 2, 3, 4})
                        .with(writeJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WORKBOOK_SIGNATURE_INVALID"));

        assertEquals(0, count("SELECT COUNT(*) FROM import_job WHERE source_system = ?", "E2E_CORRUPT"));
        assertEquals(
                objectsBefore,
                s3.listObjectsV2(ListObjectsV2Request.builder().bucket(BUCKET).build())
                        .keyCount());
    }

    @Test
    void leavesNoDatabaseStateWhenS3FailsBeforeRegistration() throws Exception {
        emptyBucket();
        s3.deleteBucket(DeleteBucketRequest.builder().bucket(BUCKET).build());
        try {
            mockMvc.perform(upload(
                                    "E2E_STORAGE_FAILURE",
                                    "storage-failure-1",
                                    fixture("supplier-workbook-v1-valid.xlsx"))
                            .with(writeJwt()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("ARTIFACT_STORAGE_UNAVAILABLE"));
            assertEquals(
                    0,
                    count(
                            "SELECT COUNT(*) FROM import_job WHERE source_system = ?",
                            "E2E_STORAGE_FAILURE"));
            assertTrue(meterRegistry
                            .get("stewardmesh.imports.stage.failures")
                            .tag("stage", "artifact_store")
                            .counter()
                            .count()
                    >= 1);
        } finally {
            s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        }
    }

    private void emptyBucket() {
        s3.listObjectsV2(ListObjectsV2Request.builder().bucket(BUCKET).build())
                .contents()
                .forEach(object -> s3.deleteObject(DeleteObjectRequest.builder()
                        .bucket(BUCKET)
                        .key(object.key())
                        .build()));
    }

    private int count(String sql, Object argument) {
        return jdbcTemplate.queryForObject(sql, Integer.class, argument);
    }

    private static MockMultipartHttpServletRequestBuilder upload(
            String sourceSystem, String idempotencyKey, byte[] workbook) {
        var part = new MockMultipartFile(
                "workbook", "synthetic.xlsx", XLSX_CONTENT_TYPE, workbook);
        return multipart("/api/v1/supplier-imports")
                .file(part)
                .param("sourceSystem", sourceSystem)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.MULTIPART_FORM_DATA);
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream input = SupplierIntakeEndToEndIT.class
                .getResourceAsStream("/fixtures/intake/" + name)) {
            assertNotNull(input, "missing fixture " + name);
            return input.readAllBytes();
        }
    }

    private static RequestPostProcessor writeJwt() {
        return authentication(new JwtAuthenticationToken(
                jwt(), List.of(new SimpleGrantedAuthority("SCOPE_supplier-import.write"))));
    }

    private static RequestPostProcessor readJwt() {
        return authentication(new JwtAuthenticationToken(
                jwt(), List.of(new SimpleGrantedAuthority("SCOPE_supplier-import.read"))));
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("synthetic-e2e-token")
                .header("alg", "none")
                .subject("synthetic-e2e-client")
                .build();
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
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

    @TestConfiguration(proxyBeanMethods = false)
    static class S3TestConfiguration {

        @Bean
        @Primary
        S3Client e2eS3Client() {
            return s3Client();
        }
    }
}
