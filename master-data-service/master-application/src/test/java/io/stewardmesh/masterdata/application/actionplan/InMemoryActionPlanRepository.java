package io.stewardmesh.masterdata.application.actionplan;

import io.stewardmesh.masterdata.application.port.out.ActionPlanRepository;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanFingerprint;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStatus;
import io.stewardmesh.masterdata.domain.actionplan.GovernedActionPlan;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Mirrors the adapter contract: insert once, advance status, one undecided plan per content. */
final class InMemoryActionPlanRepository implements ActionPlanRepository {

    private final Map<ActionPlanId, GovernedActionPlan> stored = new LinkedHashMap<>();

    private int saveCount;

    @Override
    public Optional<GovernedActionPlan> findById(ActionPlanId id) {
        return Optional.ofNullable(stored.get(id));
    }

    @Override
    public Optional<GovernedActionPlan> findProposedByFingerprint(
            ActionPlanFingerprint fingerprint) {
        return stored.values().stream()
                .filter(plan -> plan.status() == ActionPlanStatus.PROPOSED)
                .filter(plan -> plan.fingerprint().equals(fingerprint))
                .findFirst();
    }

    @Override
    public GovernedActionPlan save(GovernedActionPlan plan) {
        saveCount++;
        var current = stored.get(plan.id());
        if (current != null && !current.plan().equals(plan.plan())) {
            throw new ActionPlanConflictException("sealed action plan content cannot change");
        }
        stored.put(plan.id(), plan);
        return plan;
    }

    int saveCount() {
        return saveCount;
    }

    int size() {
        return stored.size();
    }

    void replace(GovernedActionPlan plan) {
        stored.put(plan.id(), plan);
    }
}
