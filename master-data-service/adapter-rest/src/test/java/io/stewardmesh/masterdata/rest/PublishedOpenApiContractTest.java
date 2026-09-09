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
        Object document;
        try (InputStream input = getClass()
                .getResourceAsStream("/contracts/openapi/supplier-imports-v1.yaml")) {
            assertNotNull(input, "published OpenAPI contract must be available");
            document = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
        }

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
