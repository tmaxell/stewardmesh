package io.stewardmesh.masterdata.bootstrap;

import java.util.Objects;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

/** Readiness signal for the immutable intake-artifact boundary. */
final class IntakeStorageHealthIndicator implements HealthIndicator {

    private final S3Client s3;
    private final String bucket;

    IntakeStorageHealthIndicator(S3Client s3, String bucket) {
        this.s3 = Objects.requireNonNull(s3, "s3 must not be null");
        this.bucket = Objects.requireNonNull(bucket, "bucket must not be null");
    }

    @Override
    public Health health() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return Health.up().build();
        } catch (RuntimeException exception) {
            return Health.down().build();
        }
    }
}
