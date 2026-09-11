package io.stewardmesh.masterdata.bootstrap;

import io.stewardmesh.masterdata.application.port.out.ImportIdentityGenerator;
import io.stewardmesh.masterdata.application.port.out.IntakeArtifactRepository;
import io.stewardmesh.masterdata.domain.intake.ImportPolicy;
import io.stewardmesh.masterdata.ingestion.s3.S3IntakeArtifactStorage;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.health.contributor.HealthIndicator;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration(proxyBeanMethods = false)
class IntakeStorageConfiguration {

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    ImportPolicy supplierWorkbookImportPolicy() {
        return ImportPolicy.supplierWorkbookV1();
    }

    @Bean
    ImportIdentityGenerator importIdentityGenerator() {
        return new UuidImportIdentityGenerator();
    }

    @Bean
    S3Client intakeS3Client(
            @Value("${stewardmesh.intake-storage.region}") String region,
            @Value("${stewardmesh.intake-storage.endpoint}") URI endpoint,
            @Value("${stewardmesh.intake-storage.force-path-style}") boolean forcePathStyle) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .forcePathStyle(forcePathStyle)
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofSeconds(30)));
        return builder.endpointOverride(endpoint).build();
    }

    @Bean
    S3IntakeArtifactStorage intakeArtifactStorage(
            S3Client s3,
            @Value("${stewardmesh.intake-storage.bucket}") String bucket,
            ImportIdentityGenerator identityGenerator,
            IntakeArtifactRepository artifactRepository,
            ImportPolicy importPolicy,
            Clock clock) {
        return new S3IntakeArtifactStorage(
                s3, bucket, identityGenerator, artifactRepository, importPolicy, clock);
    }

    @Bean("intakeStorageHealthIndicator")
    HealthIndicator intakeStorageHealthIndicator(
            S3Client s3, @Value("${stewardmesh.intake-storage.bucket}") String bucket) {
        return new IntakeStorageHealthIndicator(s3, bucket);
    }
}
