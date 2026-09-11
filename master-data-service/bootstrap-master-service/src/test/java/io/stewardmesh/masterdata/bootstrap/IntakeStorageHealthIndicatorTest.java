package io.stewardmesh.masterdata.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;

class IntakeStorageHealthIndicatorTest {

    @Test
    void reportsUpOnlyWhenTheConfiguredBucketIsReachable() {
        var healthy = new IntakeStorageHealthIndicator(client(false), "synthetic-bucket");
        var unavailable = new IntakeStorageHealthIndicator(client(true), "synthetic-bucket");

        assertEquals(Status.UP, healthy.health().getStatus());
        assertEquals(Status.DOWN, unavailable.health().getStatus());
        assertEquals(java.util.Map.of(), unavailable.health().getDetails());
    }

    private static S3Client client(boolean fail) {
        return (S3Client) Proxy.newProxyInstance(
                S3Client.class.getClassLoader(),
                new Class<?>[] {S3Client.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("headBucket")) {
                        if (fail) {
                            throw SdkClientException.create("synthetic health failure");
                        }
                        return HeadBucketResponse.builder().build();
                    }
                    if (method.getName().equals("serviceName")) {
                        return "s3";
                    }
                    if (method.getName().equals("close")) {
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
