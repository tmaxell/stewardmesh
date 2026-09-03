package io.stewardmesh.masterdata.domain.intake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class ImportRequestIdentityTest {

    @Test
    void scopesRepeatedKeysToTheirOriginSystem() {
        var first = new ImportRequestIdentity(
                new SourceSystemRef("SYNTHETIC_ERP"), new IdempotencyKey("request-42"));
        var replay = new ImportRequestIdentity(
                new SourceSystemRef("SYNTHETIC_ERP"), new IdempotencyKey("request-42"));
        var anotherOrigin = new ImportRequestIdentity(
                new SourceSystemRef("SYNTHETIC_SRM"), new IdempotencyKey("request-42"));

        assertEquals(first, replay);
        assertNotEquals(first, anotherOrigin);
    }
}
