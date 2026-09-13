package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.actionplan.ActionPlanExecution;
import io.stewardmesh.masterdata.application.actionplan.ExecutionRequestKey;
import java.util.Optional;

public interface ActionPlanExecutionRepository {

    Optional<ActionPlanExecution> findBySubjectAndRequestKey(
            String subject, ExecutionRequestKey requestKey);

    ActionPlanExecution save(ActionPlanExecution execution);
}
