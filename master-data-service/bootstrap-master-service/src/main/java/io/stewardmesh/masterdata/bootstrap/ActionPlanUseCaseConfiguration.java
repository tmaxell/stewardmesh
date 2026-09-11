package io.stewardmesh.masterdata.bootstrap;

import io.stewardmesh.masterdata.application.actionplan.ActionPlanProposalService;
import io.stewardmesh.masterdata.application.port.in.ProposeActionPlan;
import io.stewardmesh.masterdata.application.port.out.ActionPlanIdentityGenerator;
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
            ActionPlanIdentityGenerator identityGenerator, Clock applicationClock) {
        return new ActionPlanProposalService(identityGenerator, applicationClock);
    }
}
