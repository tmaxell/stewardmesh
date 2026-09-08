package io.stewardmesh.masterdata.ingestion.s3;

import io.stewardmesh.masterdata.application.intake.IntakeContent;
import io.stewardmesh.masterdata.application.port.out.ImportIdentityGenerator;
import io.stewardmesh.masterdata.application.port.out.IntakeArtifactRepository;
import io.stewardmesh.masterdata.application.port.out.LoadIntakeArtifact;
import io.stewardmesh.masterdata.application.port.out.StoreIntakeArtifact;
import io.stewardmesh.masterdata.domain.intake.ImportPolicy;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifact;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Immutable, content-addressed storage for original intake workbook bytes. */
public final class S3IntakeArtifactStorage implements StoreIntakeArtifact, LoadIntakeArtifact {

    public static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final String SHA_256_METADATA = "sha256";
    private static final String SIZE_METADATA = "size-bytes";
    private static final int COPY_BUFFER_BYTES = 64 * 1024;

    private final S3Client s3;
    private final String bucket;
    private final ImportIdentityGenerator identityGenerator;
    private final IntakeArtifactRepository artifactRepository;
    private final ImportPolicy importPolicy;
    private final Clock clock;

    public S3IntakeArtifactStorage(
            S3Client s3,
            String bucket,
            ImportIdentityGenerator identityGenerator,
            IntakeArtifactRepository artifactRepository,
            ImportPolicy importPolicy,
            Clock clock) {
        this.s3 = Objects.requireNonNull(s3, "s3 must not be null");
        this.bucket = requireBucket(bucket);
        this.identityGenerator =
                Objects.requireNonNull(identityGenerator, "identityGenerator must not be null");
        this.artifactRepository =
                Objects.requireNonNull(artifactRepository, "artifactRepository must not be null");
        this.importPolicy = Objects.requireNonNull(importPolicy, "importPolicy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public IntakeArtifact store(IntakeContent content) {
        Objects.requireNonNull(content, "content must not be null");
        validateContentMetadata(content);

        Path staged = null;
        try {
            staged = Files.createTempFile("stewardmesh-intake-", ".xlsx");
            StagedArtifact artifact = stage(content, staged);
            String key = storageKey(artifact.sha256());
            putIfAbsentAndVerify(staged, key, artifact);
            return new IntakeArtifact(
                    identityGenerator.nextArtifactId(),
                    artifact.sha256(),
                    key,
                    content.contentType(),
                    artifact.sizeBytes(),
                    clock.instant());
        } catch (IOException exception) {
            throw new ArtifactStorageException("intake artifact could not be staged", exception);
        } finally {
            deleteStagedFile(staged);
        }
    }

    @Override
    public IntakeContent load(IntakeArtifactId artifactId) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        IntakeArtifact artifact = artifactRepository
                .findById(artifactId)
                .orElseThrow(() -> new ArtifactStorageException("intake artifact metadata was not found"));
        verifyStoredObject(artifact.storageKey(), artifact.sha256(), artifact.sizeBytes());
        return new StoredIntakeContent(artifact);
    }

    private void validateContentMetadata(IntakeContent content) {
        if (!XLSX_CONTENT_TYPE.equals(content.contentType())) {
            throw new IllegalArgumentException("only the supplier XLSX content type is supported");
        }
        if (content.sizeBytes() <= 0 || content.sizeBytes() > importPolicy.maxUploadBytes()) {
            throw new IllegalArgumentException("intake artifact size is outside the configured limit");
        }
    }

    private StagedArtifact stage(IntakeContent content, Path staged) throws IOException {
        MessageDigest digest = sha256Digest();
        long size = 0;
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        try (InputStream input = content.openStream(); OutputStream output = Files.newOutputStream(staged)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                size = Math.addExact(size, read);
                if (size > importPolicy.maxUploadBytes()) {
                    throw new IllegalArgumentException("intake artifact exceeds the configured byte limit");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        if (size != content.sizeBytes()) {
            throw new IllegalArgumentException("intake artifact size does not match its declared size");
        }
        return new StagedArtifact(HexFormat.of().formatHex(digest.digest()), size);
    }

    private void putIfAbsentAndVerify(Path staged, String key, StagedArtifact artifact) {
        if (objectExists(key)) {
            verifyStoredObject(key, artifact.sha256(), artifact.sizeBytes());
            return;
        }
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(XLSX_CONTENT_TYPE)
                .contentLength(artifact.sizeBytes())
                .metadata(Map.of(
                        SHA_256_METADATA, artifact.sha256(),
                        SIZE_METADATA, Long.toString(artifact.sizeBytes())))
                .ifNoneMatch("*")
                .build();
        try {
            s3.putObject(request, RequestBody.fromFile(staged));
        } catch (S3Exception exception) {
            if (exception.statusCode() != 412) {
                throw new ArtifactStorageException("intake artifact could not be stored", exception);
            }
        }
        verifyStoredObject(key, artifact.sha256(), artifact.sizeBytes());
    }

    private boolean objectExists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw new ArtifactStorageException("intake artifact presence could not be checked", exception);
        }
    }

    private void verifyStoredObject(String key, String expectedSha256, long expectedSize) {
        HeadObjectResponse response;
        try {
            response = s3.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception exception) {
            throw new ArtifactStorageException("intake artifact is unavailable", exception);
        }
        if (response.contentLength() != expectedSize
                || !expectedSha256.equals(response.metadata().get(SHA_256_METADATA))
                || !Long.toString(expectedSize).equals(response.metadata().get(SIZE_METADATA))) {
            throw new ArtifactStorageException("intake artifact metadata does not match stored content");
        }
    }

    private static String storageKey(String sha256) {
        return "intake/sha256/" + sha256.substring(0, 2) + "/" + sha256 + ".xlsx";
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private static String requireBucket(String bucket) {
        String value = Objects.requireNonNull(bucket, "bucket must not be null").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("bucket must not be blank");
        }
        return value;
    }

    private static void deleteStagedFile(Path staged) {
        if (staged == null) {
            return;
        }
        try {
            Files.deleteIfExists(staged);
        } catch (IOException exception) {
            staged.toFile().deleteOnExit();
        }
    }

    private record StagedArtifact(String sha256, long sizeBytes) {}

    private final class StoredIntakeContent implements IntakeContent {

        private final IntakeArtifact artifact;

        private StoredIntakeContent(IntakeArtifact artifact) {
            this.artifact = artifact;
        }

        @Override
        public String contentType() {
            return artifact.contentType();
        }

        @Override
        public long sizeBytes() {
            return artifact.sizeBytes();
        }

        @Override
        public InputStream openStream() {
            InputStream input = s3.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(artifact.storageKey())
                    .build());
            return new ChecksumVerifyingInputStream(input, artifact.sha256(), artifact.sizeBytes());
        }
    }

    private static final class ChecksumVerifyingInputStream extends FilterInputStream {

        private final MessageDigest digest = sha256Digest();
        private final String expectedSha256;
        private final long expectedSize;
        private long size;
        private boolean verified;

        private ChecksumVerifyingInputStream(
                InputStream input, String expectedSha256, long expectedSize) {
            super(input);
            this.expectedSha256 = expectedSha256;
            this.expectedSize = expectedSize;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value == -1) {
                verify();
            } else {
                digest.update((byte) value);
                size++;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read == -1) {
                verify();
            } else {
                digest.update(bytes, offset, read);
                size = Math.addExact(size, read);
            }
            return read;
        }

        private void verify() throws IOException {
            if (verified) {
                return;
            }
            verified = true;
            String actualSha256 = HexFormat.of().formatHex(digest.digest());
            if (size != expectedSize || !actualSha256.equals(expectedSha256)) {
                throw new IOException("stored intake artifact failed checksum verification");
            }
        }
    }
}
