package io.stewardmesh.masterdata.application.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateBlockingBoundaryTest {

    @Test
    void exposesBoundedPagesWithStableBlockingEvidence() {
        var candidate = new PartyCandidateBlock(
                new SupplierPartyId(UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb201")),
                Set.of(BlockingKeyType.PARTY_INN_EXACT));
        var page = new CandidateBlockPage<>(1, true, List.of(candidate));
        var result = new MatchCandidateBlocks(
                identity(), page, new CandidateBlockPage<>(1, false, List.of()));

        assertEquals(candidate, result.parties().candidates().getFirst());
        assertEquals(1, result.parties().limit());
        assertTrue(result.parties().truncated());
    }

    @Test
    void rejectsUnboundedRequestsAndCrossEntityEvidence() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GenerateMatchCandidatesCommand(identity(), 101));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PartyCandidateBlock(
                        new SupplierPartyId(UUID.randomUUID()),
                        Set.of(BlockingKeyType.SITE_CODE_EXACT)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CandidateBlockPage<>(1, false, List.of("one", "two")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PartyBlockingKeys("٩٩٠٢٠٠٠٠٠٥", null));
    }

    @Test
    void requiresSiteSpecificContextBeforeBuildingSiteKeys() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SiteBlockingKeys(
                        "9902000005",
                        null,
                        null,
                        "RU",
                        "100001",
                        "TEST REGION",
                        "TEST CITY",
                        "TEST ADDRESS"));
    }

    private static SourceRecordIdentity identity() {
        return new SourceRecordIdentity(
                new SourceSystemRef("SYNTHETIC_ERP"), "source-1", 1);
    }
}
