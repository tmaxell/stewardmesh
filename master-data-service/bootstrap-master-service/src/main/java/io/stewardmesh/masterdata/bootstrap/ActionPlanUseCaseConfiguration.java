package io.stewardmesh.masterdata.bootstrap;

import io.stewardmesh.masterdata.application.actionplan.ActionPlanApprovalService;
import io.stewardmesh.masterdata.application.actionplan.ActionPlanExecutionService;
import io.stewardmesh.masterdata.application.actionplan.ActionPlanProposalService;
import io.stewardmesh.masterdata.application.actionplan.ActionPlanReadService;
import io.stewardmesh.masterdata.application.actionplan.ActionPlanSimulationService;
import io.stewardmesh.masterdata.application.actionplan.LocalActionPlanStepApplier;
import io.stewardmesh.masterdata.application.port.in.AssignSupplierSite;
import io.stewardmesh.masterdata.application.port.in.DecideActionPlan;
import io.stewardmesh.masterdata.application.port.in.ExecuteActionPlan;
import io.stewardmesh.masterdata.application.port.in.GetActionPlan;
import io.stewardmesh.masterdata.application.port.in.ProjectSourceRecord;
import io.stewardmesh.masterdata.application.port.in.ProposeActionPlan;
import io.stewardmesh.masterdata.application.port.in.SimulateActionPlan;
import io.stewardmesh.masterdata.application.port.out.ActionPlanApprovalRepository;
import io.stewardmesh.masterdata.application.port.out.ActionPlanExecutionRepository;
import io.stewardmesh.masterdata.application.port.out.ActionPlanIdentityGenerator;
import io.stewardmesh.masterdata.application.port.out.ActionPlanRepository;
import io.stewardmesh.masterdata.application.port.out.ActionPlanStepApplier;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.application.port.out.AuditEventRepository;
import io.stewardmesh.masterdata.application.port.out.BusinessUnitRepository;
import io.stewardmesh.masterdata.application.port.out.ExecutionIdentityGenerator;
import io.stewardmesh.masterdata.application.port.out.LoadGoldenRecordProjection;
import io.stewardmesh.masterdata.application.port.out.LoadSourceRecord;
import io.stewardmesh.masterdata.application.port.out.OutboxEventRepository;
import io.stewardmesh.masterdata.application.port.out.SiteAssignmentRepository;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanApprovalPolicy;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanExecutionPolicy;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanSimulator;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ActionPlanUseCaseConfiguration {

    @Bean
    ActionPlanApprovalPolicy actionPlanApprovalPolicy() {
        return new ActionPlanApprovalPolicy();
    }

    @Bean
    DecideActionPlan decideActionPlan(
            ActionPlanRepository actionPlans,
            ActionPlanApprovalRepository approvals,
            ApplicationTransaction transaction,
            ActionPlanApprovalPolicy policy,
            Clock applicationClock) {
        return new ActionPlanApprovalService(
                actionPlans, approvals, transaction, policy, applicationClock);
    }

    @Bean
    ActionPlanIdentityGenerator actionPlanIdentityGenerator() {
        return new UuidActionPlanIdentityGenerator();
    }

    @Bean
    ProposeActionPlan proposeActionPlan(
            ActionPlanRepository actionPlans,
            ActionPlanIdentityGenerator identityGenerator,
            ApplicationTransaction transaction,
            Clock applicationClock) {
        return new ActionPlanProposalService(
                actionPlans, identityGenerator, transaction, applicationClock);
    }

    @Bean
    GetActionPlan getActionPlan(ActionPlanRepository actionPlans) {
        return new ActionPlanReadService(actionPlans);
    }

    @Bean
    ActionPlanSimulator actionPlanSimulator() {
        return new ActionPlanSimulator();
    }

    @Bean
    SimulateActionPlan simulateActionPlan(
            ActionPlanRepository actionPlans,
            LoadGoldenRecordProjection goldenRecords,
            BusinessUnitRepository businessUnits,
            SiteAssignmentRepository assignments,
            LoadSourceRecord sourceRecords,
            ActionPlanSimulator simulator) {
        return new ActionPlanSimulationService(
                actionPlans, goldenRecords, businessUnits, assignments, sourceRecords, simulator);
    }

    @Bean
    ActionPlanExecutionPolicy actionPlanExecutionPolicy() {
        return new ActionPlanExecutionPolicy();
    }

    @Bean
    ExecutionIdentityGenerator executionIdentityGenerator() {
        return UUID::randomUUID;
    }

    @Bean
    ActionPlanStepApplier actionPlanStepApplier(
            ProjectSourceRecord projectSourceRecord,
            LoadGoldenRecordProjection goldenRecords,
            AssignSupplierSite assignSupplierSite) {
        return new LocalActionPlanStepApplier(
                projectSourceRecord, goldenRecords, assignSupplierSite);
    }

    @Bean
    ExecuteActionPlan executeActionPlan(
            ActionPlanRepository actionPlans,
            ActionPlanApprovalRepository approvals,
            ActionPlanExecutionRepository executions,
            SimulateActionPlan simulations,
            ActionPlanStepApplier stepApplier,
            AuditEventRepository auditEvents,
            OutboxEventRepository outboxEvents,
            ApplicationTransaction transaction,
            ExecutionIdentityGenerator identities,
            ActionPlanExecutionPolicy policy,
            Clock applicationClock) {
        return new ActionPlanExecutionService(
                actionPlans, approvals, executions, simulations, stepApplier, auditEvents,
                outboxEvents, transaction, identities, policy, applicationClock);
    }
}
