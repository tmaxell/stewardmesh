package io.stewardmesh.masterdata.bootstrap;

import io.stewardmesh.masterdata.application.actionplan.ActionPlanProposalService;
import io.stewardmesh.masterdata.application.actionplan.ActionPlanReadService;
import io.stewardmesh.masterdata.application.actionplan.ActionPlanSimulationService;
import io.stewardmesh.masterdata.application.port.in.GetActionPlan;
import io.stewardmesh.masterdata.application.port.in.ProposeActionPlan;
import io.stewardmesh.masterdata.application.port.in.SimulateActionPlan;
import io.stewardmesh.masterdata.application.port.out.ActionPlanIdentityGenerator;
import io.stewardmesh.masterdata.application.port.out.ActionPlanRepository;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.application.port.out.BusinessUnitRepository;
import io.stewardmesh.masterdata.application.port.out.LoadGoldenRecordProjection;
import io.stewardmesh.masterdata.application.port.out.LoadSourceRecord;
import io.stewardmesh.masterdata.application.port.out.SiteAssignmentRepository;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanSimulator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ActionPlanUseCaseConfiguration {

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
}
