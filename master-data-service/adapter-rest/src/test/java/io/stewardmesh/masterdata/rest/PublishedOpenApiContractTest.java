package io.stewardmesh.masterdata.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

class PublishedOpenApiContractTest {

    @Test
    void publishesTheThreeVersionedOperationsAndOAuthScopes() throws IOException {
        Object document = document("supplier-imports-v1.yaml");

        Map<?, ?> root = map(document);
        assertEquals("3.1.0", root.get("openapi"));
        Map<?, ?> paths = map(root.get("paths"));
        assertEquals(
                Set.of(
                        "/api/v1/supplier-imports",
                        "/api/v1/supplier-imports/{importId}",
                        "/api/v1/supplier-imports/{importId}/report"),
                paths.keySet());
        assertSecurityScope(paths, "/api/v1/supplier-imports", "post", "supplier-import.write");
        assertSecurityScope(
                paths, "/api/v1/supplier-imports/{importId}", "get", "supplier-import.read");
        assertSecurityScope(
                paths,
                "/api/v1/supplier-imports/{importId}/report",
                "get",
                "supplier-import.read");
    }

    @Test
    void publishesOnlyFourBoundedIdentityResolutionReads() throws IOException {
        Map<?, ?> root = map(document("identity-resolution-v1.yaml"));
        assertEquals("3.1.0", root.get("openapi"));
        Map<?, ?> paths = map(root.get("paths"));
        assertEquals(Set.of(
                "/api/v1/identity-resolution/sources/{originSystem}/{sourceRecordId}/versions/{sourceVersion}",
                "/api/v1/identity-resolution/sources/{originSystem}/{sourceRecordId}/versions/{sourceVersion}/candidates",
                "/api/v1/identity-resolution/sources/{originSystem}/{sourceRecordId}/versions/{sourceVersion}/candidates/{candidateId}/explanation",
                "/api/v1/golden-records/{entityType}/{entityId}"), paths.keySet());
        paths.forEach((path, operation) -> {
            Map<?, ?> methods = map(operation);
            assertEquals(Set.of("get"), methods.keySet(), "identity contract must be read-only");
            assertSecurityScope(paths, path.toString(), "get", "identity-resolution.read");
        });
        Map<?, ?> candidateParameters = map(map(paths.get(
                "/api/v1/identity-resolution/sources/{originSystem}/{sourceRecordId}/versions/{sourceVersion}/candidates"))
                .get("get"));
        assertTrue(candidateParameters.get("parameters").toString().contains("maximum=100"));
    }

    private Object document(String name) throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/contracts/openapi/" + name)) {
            assertNotNull(input, "published OpenAPI contract must be available");
            return new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
        }
    }

    private static void assertSecurityScope(
            Map<?, ?> paths, String path, String method, String expectedScope) {
        Object security = map(map(paths.get(path)).get(method)).get("security");
        assertTrue(security.toString().contains(expectedScope));
    }

    private static Map<?, ?> map(Object value) {
        if (value instanceof Map<?, ?> result) {
            return result;
        }
        return fail("expected a mapping in the published OpenAPI contract");
    }
}
