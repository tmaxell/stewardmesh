package io.stewardmesh.masterdata.application.goldenrecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceMasterLinkReadServiceTest {

    private static final SourceRecordIdentity SOURCE =
            new SourceRecordIdentity(new SourceSystemRef("SYNTHETIC_ERP"), "source-1", 1);
    private static final SourceMasterLink LINK = new SourceMasterLink(
            SOURCE, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Test
    void returnsOnlyUniqueActiveLink() {
        var service = new SourceMasterLinkReadService(source -> List.of(LINK));
        assertEquals(LINK, service.execute(SOURCE));
    }

    @Test
    void distinguishesAbsentFromAmbiguousMapping() {
        var missing = new SourceMasterLinkReadService(source -> List.of());
        assertFalse(assertThrows(SourceMasterLinkReadException.class,
                () -> missing.execute(SOURCE)).ambiguous());
        var ambiguous = new SourceMasterLinkReadService(source -> List.of(LINK, LINK));
        assertTrue(assertThrows(SourceMasterLinkReadException.class,
                () -> ambiguous.execute(SOURCE)).ambiguous());
    }

    @Test
    void rejectsPartialSiteTargets() {
        assertThrows(IllegalArgumentException.class, () -> new SourceMasterLink(
                SOURCE, UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID()));
    }
}
