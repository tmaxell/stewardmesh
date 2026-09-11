package io.stewardmesh.masterdata.application.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
import io.stewardmesh.masterdata.domain.identity.MatchFeature;
import io.stewardmesh.masterdata.domain.identity.MatchFeatureCode;
import io.stewardmesh.masterdata.domain.identity.MatchOutcome;
import io.stewardmesh.masterdata.domain.identity.MatchRulesetId;
import io.stewardmesh.masterdata.domain.identity.MatchSignal;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentityResolutionReadServiceTest {

    private static final IdentityResolutionKey KEY = new IdentityResolutionKey(
            new SourceRecordIdentity(new SourceSystemRef("SYNTHETIC_ERP"), "source-1", 1),
            new MatchRulesetId("supplier-match-v1"));
    private static final UUID PARTY_1 = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PARTY_2 = UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Test
    void returnsBalancedStatusAndBoundedDeterministicPages() {
        var evaluations = (io.stewardmesh.masterdata.application.port.out.LoadMatchEvaluation)
                key -> Optional.of(evaluation());
        var statusService = new IdentityResolutionStatusService(evaluations);
        var candidatesService = new IdentityResolutionCandidatesService(evaluations);

        IdentityResolutionStatus status = statusService.execute(KEY);
        var page = candidatesService.execute(
                new IdentityResolutionCandidateQuery(KEY, MatchEntityType.PARTY, 1, 1));

        assertEquals(2, status.partyCandidates());
        assertEquals(1, status.autoLinks());
        assertEquals(1, status.reviews());
        assertTrue(status.hardConflict());
        assertEquals(2, page.totalCandidates());
        assertEquals(PARTY_2, page.candidates().getFirst().candidateId());
        assertThrows(IllegalArgumentException.class,
                () -> new IdentityResolutionCandidateQuery(KEY, MatchEntityType.PARTY, 0, 101));
    }

    @Test
    void returnsOneExplanationAndStableNotFoundFailures() {
        var evaluations = (io.stewardmesh.masterdata.application.port.out.LoadMatchEvaluation)
                key -> key.equals(KEY) ? Optional.of(evaluation()) : Optional.empty();
        var explanationService = new MatchExplanationService(evaluations);
        var statusService = new IdentityResolutionStatusService(evaluations);

        MatchDecision explanation = explanationService.execute(
                new MatchExplanationQuery(KEY, MatchEntityType.PARTY, PARTY_2));

        assertTrue(explanation.hardConflict());
        assertEquals(MatchFeatureCode.AUTHORITATIVE_IDENTIFIER_CONFLICT,
                explanation.features().getFirst().code());
        assertThrows(MatchCandidateNotFoundException.class, () -> explanationService.execute(
                new MatchExplanationQuery(KEY, MatchEntityType.SITE, PARTY_1)));
        var absent = new IdentityResolutionKey(
                new SourceRecordIdentity(new SourceSystemRef("SYNTHETIC_ERP"), "absent", 1),
                KEY.rulesetId());
        assertThrows(IdentityResolutionNotFoundException.class, () -> statusService.execute(absent));
    }

    private static MatchEvaluation evaluation() {
        return new MatchEvaluation(
                KEY.sourceRecordIdentity(), KEY.rulesetId(), Instant.parse("2026-09-11T08:00:00Z"),
                List.of(
                        decision(PARTY_2, MatchOutcome.REVIEW, true),
                        decision(PARTY_1, MatchOutcome.AUTO_LINK, false)),
                List.of());
    }

    private static MatchDecision decision(UUID id, MatchOutcome outcome, boolean conflict) {
        return new MatchDecision(
                MatchEntityType.PARTY, id, outcome, conflict ? 0 : 10_000, KEY.rulesetId(), conflict,
                List.of(new MatchFeature(
                        conflict ? MatchFeatureCode.AUTHORITATIVE_IDENTIFIER_CONFLICT
                                : MatchFeatureCode.INN_EXACT,
                        conflict ? MatchSignal.CONFLICT : MatchSignal.MATCH,
                        conflict ? -10_000 : 10_000)));
    }
}
