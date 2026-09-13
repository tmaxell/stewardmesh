package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.domain.actionplan.ActionPlanApproval;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ApprovalRequestKey;
import java.util.Optional;

/** Stores immutable approval decisions independently from sealed plan content. */
public interface ActionPlanApprovalRepository {

    Optional<ActionPlanApproval> findByPlanId(ActionPlanId planId);

    Optional<ActionPlanApproval> findBySubjectAndRequestKey(
            String subject, ApprovalRequestKey requestKey);

    ActionPlanApproval save(ActionPlanApproval approval);
}
