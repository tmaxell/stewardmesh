package io.stewardmesh.masterdata.bootstrap;

import io.micrometer.core.instrument.MeterRegistry;
import io.stewardmesh.masterdata.application.actionplan.AuthenticatedApprovalActor;
import io.stewardmesh.masterdata.application.actionplan.DecideActionPlanCommand;
import io.stewardmesh.masterdata.application.port.in.DecideActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanApproval;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

/**
 * Counts human decisions outside the database transaction that records them, so a metric failure can
 * never roll back a governed approval. Labels stay bounded: the decision, and whether the caller
 * replayed an idempotency key. Subjects, plan hashes and reasons are never labels.
 */
final class MeteredDecideActionPlan implements DecideActionPlan {

    private final DecideActionPlan delegate;
    private final MeterRegistry registry;
    private final Clock clock;

    MeteredDecideActionPlan(DecideActionPlan delegate, MeterRegistry registry, Clock clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public ActionPlanApproval decide(
            DecideActionPlanCommand command, AuthenticatedApprovalActor authenticatedActor) {
        var requestedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        try {
            ActionPlanApproval approval = delegate.decide(command, authenticatedActor);
            registry.counter(
                            "stewardmesh.actionplan.decisions",
                            "decision",
                            tag(approval.resultingStatus().name()),
                            "replayed",
                            Boolean.toString(approval.decidedAt().isBefore(requestedAt)))
                    .increment();
            return approval;
        } catch (RuntimeException refused) {
            registry.counter(
                            "stewardmesh.actionplan.decisions.refusals",
                            "reason",
                            tag(refused.getClass().getSimpleName()))
                    .increment();
            throw refused;
        }
    }

    private static String tag(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
