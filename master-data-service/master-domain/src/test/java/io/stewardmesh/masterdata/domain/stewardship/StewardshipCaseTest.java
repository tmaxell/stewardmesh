package io.stewardmesh.masterdata.domain.stewardship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.domain.identity.MatchRulesetId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StewardshipCaseTest {

    @Test
    void retainsImmutableSourceAndRulesetLineage() {
        var identity = new SourceRecordIdentity(new SourceSystemRef("ERP"), "supplier-42", 3);
        var ruleset = new MatchRulesetId("supplier-identity-v1");
        var openedAt = Instant.parse("2026-09-10T10:00:00Z");

        var reviewCase = new StewardshipCase(
                identity, ruleset, StewardshipCaseReason.AUTHORITATIVE_CONFLICT, openedAt);

        assertEquals(identity, reviewCase.sourceRecordIdentity());
        assertEquals(ruleset, reviewCase.rulesetId());
        assertEquals(StewardshipCaseReason.AUTHORITATIVE_CONFLICT, reviewCase.reason());
        assertEquals(openedAt, reviewCase.openedAt());
    }

    @Test
    void rejectsIncompleteLineage() {
        assertThrows(
                NullPointerException.class,
                () -> new StewardshipCase(
                        null,
                        new MatchRulesetId("supplier-identity-v1"),
                        StewardshipCaseReason.AMBIGUOUS_MATCH,
                        Instant.EPOCH));
    }
}
