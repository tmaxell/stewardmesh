package io.stewardmesh.masterdata.ingestion.s3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.application.intake.IntakeContent;
import io.stewardmesh.masterdata.application.port.out.ImportIdentityGenerator;
import io.stewardmesh.masterdata.application.port.out.IntakeArtifactRepository;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ImportPolicy;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifact;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Testcontainers
class S3IntakeArtifactStorageIT {

    private static final String BUCKET = "stewardmesh-intake-test";
    private static final byte[] WORKBOOK = "synthetic-xlsx-content".getBytes(StandardCharsets.UTF_8);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC);

    @Container
    static final GenericContainer<?> LOCALSTACK = new GenericContainer<>(
                    DockerImageName.parse("localstack/localstack:4.14.0"))
            .withEnv("SERVICES", "s3")
            .withExposedPorts(4566)
            .waitingFor(Wait.forHttp("/_localstack/health").forStatusCode(200));

    private static S3Client s3;

    private InMemoryArtifactRepository repository;
    private S3IntakeArtifactStorage storage;

    @BeforeAll
    static void createClientAndBucket() {
        URI endpoint = URI.create("http://" + LOCALSTACK.getHost() + ':' + LOCALSTACK.getMappedPort(4566));
        s3 = S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(endpoint)
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .httpClientBuilder(ApacheHttpClient.builder())
                .build();
        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }

    @AfterAll
    static void closeClient() {
        if (s3 != null) {
            s3.close();
        }
    }

    @BeforeEach
    void resetStorage() {
        s3.listObjectsV2(ListObjectsV2Request.builder().bucket(BUCKET).build())
                .contents()
                .forEach(object -> s3.deleteObject(DeleteObjectRequest.builder()
                        .bucket(BUCKET)
                        .key(object.key())
                        .build()));
        repository = new InMemoryArtifactRepository();
        storage = new S3IntakeArtifactStorage(
                s3,
                BUCKET,
                new SequenceIdentityGenerator(),
                repository,
                ImportPolicy.supplierWorkbookV1(),
                CLOCK);
    }

    @Test
    void storesIdenticalContentOnceAndReturnsVerifiedMetadata() {
        IntakeArtifact first = storage.store(new ByteArrayContent(WORKBOOK, WORKBOOK.length));
        IntakeArtifact replay = storage.store(new ByteArrayContent(WORKBOOK, WORKBOOK.length));

        assertEquals(first.sha256(), replay.sha256());
        assertEquals(first.storageKey(), replay.storageKey());
        assertNotEquals(first.id(), replay.id());
        assertEquals(1, s3.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(BUCKET)
                        .build())
                .keyCount());
        var stored = s3.headObject(HeadObjectRequest.builder()
                .bucket(BUCKET)
                .key(first.storageKey())
                .build());
        assertEquals(WORKBOOK.length, first.sizeBytes());
        assertEquals(first.sha256(), stored.metadata().get("sha256"));
        assertEquals(Long.toString(first.sizeBytes()), stored.metadata().get("size-bytes"));
        assertEquals(CLOCK.instant(), first.createdAt());
    }

    @Test
    void loadsContentByPersistedArtifactIdentityAndVerifiesItsChecksum() throws IOException {
        IntakeArtifact artifact = storage.store(new ByteArrayContent(WORKBOOK, WORKBOOK.length));
        repository.save(artifact);

        try (InputStream input = storage.load(artifact.id()).openStream()) {
            assertArrayEquals(WORKBOOK, input.readAllBytes());
        }
    }

    @Test
    void rejectsContentWhoseDeclaredSizeDoesNotMatchTheStream() {
        assertThrows(
                IllegalArgumentException.class,
                () -> storage.store(new ByteArrayContent(WORKBOOK, WORKBOOK.length - 1L)));
        assertEquals(0, s3.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(BUCKET)
                        .build())
                .keyCount());
    }

    @Test
    void detectsObjectContentChangedOutsideTheImmutableAdapter() throws IOException {
        IntakeArtifact artifact = storage.store(new ByteArrayContent(WORKBOOK, WORKBOOK.length));
        repository.save(artifact);
        byte[] changed = "tampered-xlsx-content!".getBytes(StandardCharsets.UTF_8);
        assertEquals(WORKBOOK.length, changed.length);
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(BUCKET)
                        .key(artifact.storageKey())
                        .contentType(artifact.contentType())
                        .metadata(Map.of(
                                "sha256", artifact.sha256(),
                                "size-bytes", Long.toString(artifact.sizeBytes())))
                        .build(),
                RequestBody.fromBytes(changed));

        try (InputStream input = storage.load(artifact.id()).openStream()) {
            assertThrows(IOException.class, input::readAllBytes);
        }
    }

    private record ByteArrayContent(byte[] bytes, long declaredSize) implements IntakeContent {

        @Override
        public String contentType() {
            return S3IntakeArtifactStorage.XLSX_CONTENT_TYPE;
        }

        @Override
        public long sizeBytes() {
            return declaredSize;
        }

        @Override
        public InputStream openStream() {
            return new ByteArrayInputStream(bytes);
        }
    }

    private static final class SequenceIdentityGenerator implements ImportIdentityGenerator {

        private final AtomicLong sequence = new AtomicLong();

        @Override
        public ImportJobId nextImportJobId() {
            return new ImportJobId(new UUID(0, sequence.incrementAndGet()));
        }

        @Override
        public IntakeArtifactId nextArtifactId() {
            return new IntakeArtifactId(new UUID(0, sequence.incrementAndGet()));
        }
    }

    private static final class InMemoryArtifactRepository implements IntakeArtifactRepository {

        private final Map<IntakeArtifactId, IntakeArtifact> artifacts = new HashMap<>();

        @Override
        public Optional<IntakeArtifact> findById(IntakeArtifactId artifactId) {
            return Optional.ofNullable(artifacts.get(artifactId));
        }

        @Override
        public Optional<IntakeArtifact> findBySha256(String sha256) {
            return artifacts.values().stream()
                    .filter(artifact -> artifact.sha256().equals(sha256))
                    .findFirst();
        }

        @Override
        public void save(IntakeArtifact artifact) {
            artifacts.put(artifact.id(), artifact);
        }
    }
}
