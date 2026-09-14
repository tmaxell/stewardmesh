package io.stewardmesh.masterdata.mcp;

import io.stewardmesh.masterdata.application.port.in.DecideActionPlan;
import io.stewardmesh.masterdata.application.port.in.ExecuteActionPlan;
import io.stewardmesh.masterdata.application.port.in.GetActionPlan;
import io.stewardmesh.masterdata.application.port.in.ProposeActionPlan;
import io.stewardmesh.masterdata.application.port.in.ProfileIntakeArtifact;
import io.stewardmesh.masterdata.application.port.in.PreviewMappedRecords;
import io.stewardmesh.masterdata.application.port.in.SimulateActionPlan;
import io.stewardmesh.masterdata.application.port.in.SuggestIntakeMapping;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class McpToolConfiguration {

    @Bean
    GovernedActionPlanMcpTools governedActionPlanMcpTools(
            GetActionPlan getActionPlan,
            SimulateActionPlan simulateActionPlan,
            ProposeActionPlan proposeActionPlan,
            DecideActionPlan decideActionPlan,
            ExecuteActionPlan executeActionPlan) {
        return new GovernedActionPlanMcpTools(
                getActionPlan,
                simulateActionPlan,
                proposeActionPlan,
                decideActionPlan,
                executeActionPlan);
    }

    @Bean
    IntakeProfilingMcpTools intakeProfilingMcpTools(
            ProfileIntakeArtifact profileIntakeArtifact,
            SuggestIntakeMapping suggestIntakeMapping,
            PreviewMappedRecords previewMappedRecords) {
        return new IntakeProfilingMcpTools(
                profileIntakeArtifact, suggestIntakeMapping, previewMappedRecords);
    }

    @Bean
    ToolCallbackProvider masterDataToolCallbacks(
            GovernedActionPlanMcpTools governedTools,
            IntakeProfilingMcpTools profilingTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(governedTools, profilingTools)
                .build();
    }
}
