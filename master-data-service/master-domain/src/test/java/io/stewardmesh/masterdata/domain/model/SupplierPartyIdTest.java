package io.stewardmesh.masterdata.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupplierPartyIdTest {

    @Test
    void preservesFrameworkFreeValueSemantics() {
        var value = UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb10a");

        assertEquals(new SupplierPartyId(value), new SupplierPartyId(value));
        assertThrows(NullPointerException.class, () -> new SupplierPartyId(null));
    }
}
