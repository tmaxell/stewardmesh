package io.stewardmesh.masterdata.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.stewardmesh.masterdata.application.identity.MatchEvaluation;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MicrometerMatchScoringTelemetryTest {

    private static final MatchRulesetId RULESET = new MatchRulesetId("supplier-identity-v1");

    @Test
    void recordsOnlyBoundedDecisionDimensions() {
        var registry = new SimpleMeterRegistry();
        var telemetry = new MicrometerMatchScoringTelemetry(registry);
        var evaluation = new MatchEvaluation(
                new SourceRecordIdentity(new SourceSystemRef("ERP"), "sensitive-source-id", 1),
                RULESET,
                Instant.parse("2026-09-10T10:00:00Z"),
                List.of(decision(MatchEntityType.PARTY, MatchOutcome.AUTO_LINK, false)),
                List.of(decision(MatchEntityType.SITE, MatchOutcome.REVIEW, true)));

        telemetry.measure(() -> {
            telemetry.record(evaluation);
            return evaluation;
        });

        assertEquals(1, registry.get("stewardmesh.matching.scoring.duration").timer().count());
        assertEquals(1.0, registry.get("stewardmesh.matching.candidate.count")
                .tag("entity", "party").summary().totalAmount());
        assertEquals(1.0, registry.get("stewardmesh.matching.decisions")
                .tag("entity", "site")
                .tag("outcome", "review")
                .tag("hard_conflict", "true")
                .counter().count());
        registry.getMeters().forEach(meter -> meter.getId().getTags().forEach(tag -> {
            assertEquals(false, tag.getValue().contains("sensitive"));
        }));
    }

    @Test
    void recordsFailuresAndRethrowsThem() {
        var registry = new SimpleMeterRegistry();
        var telemetry = new MicrometerMatchScoringTelemetry(registry);

        assertThrows(IllegalStateException.class, () -> telemetry.measure(() -> {
            throw new IllegalStateException("synthetic failure");
        }));

        assertEquals(1.0, registry.get("stewardmesh.matching.scoring.failures").counter().count());
        assertEquals(1, registry.get("stewardmesh.matching.scoring.duration").timer().count());
    }

    private static MatchDecision decision(
            MatchEntityType entityType, MatchOutcome outcome, boolean hardConflict) {
        return new MatchDecision(
                entityType,
                UUID.randomUUID(),
                outcome,
                outcome == MatchOutcome.AUTO_LINK ? 8_500 : 6_000,
                RULESET,
                hardConflict,
                List.of(new MatchFeature(
                        hardConflict
                                ? MatchFeatureCode.AUTHORITATIVE_IDENTIFIER_CONFLICT
                                : MatchFeatureCode.INN_EXACT,
                        hardConflict ? MatchSignal.CONFLICT : MatchSignal.MATCH,
                        hardConflict ? -4_000 : 4_000)));
    }
}
