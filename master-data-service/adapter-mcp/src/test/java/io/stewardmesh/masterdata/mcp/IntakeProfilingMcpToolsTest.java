package io.stewardmesh.masterdata.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.application.intake.IntakeArtifactProfile;
import io.stewardmesh.masterdata.application.intake.IntakeMappingSuggestion;
import io.stewardmesh.masterdata.application.intake.MappedColumnPreview;
import io.stewardmesh.masterdata.application.intake.MappedRecordsPreview;
import io.stewardmesh.masterdata.application.intake.MappedRecordsReadiness;
import io.stewardmesh.masterdata.application.intake.MappingDecision;
import io.stewardmesh.masterdata.application.intake.SuggestedColumnMapping;
import io.stewardmesh.masterdata.application.intake.SupplierWorkbookColumnProfile;
import io.stewardmesh.masterdata.application.intake.SupplierWorkbookProfile;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class IntakeProfilingMcpToolsTest {

    private static final ImportJobId IMPORT_ID = new ImportJobId(
            UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb10a"));
    private static final IntakeArtifactId ARTIFACT_ID = new IntakeArtifactId(
            UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb10b"));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publishesThreeReadOnlyValueFreeToolSchemas() {
        var provider = MethodToolCallbackProvider.builder()
                .toolObjects(tools(ignored -> profile()))
                .build();
        var callbacks = provider.getToolCallbacks();

        assertEquals(3, callbacks.length);
        assertEquals(
                Set.of("profile_intake_artifact", "suggest_schema_mapping", "preview_mapped_records"),
                java.util.Arrays.stream(callbacks)
                        .map(callback -> callback.getToolDefinition().name())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        for (var callback : callbacks) {
            assertTrue(callback.getToolDefinition().description().startsWith("READ:"));
            assertTrue(callback.getToolDefinition().inputSchema().contains("importId"));
            assertFalse(callback.getToolDefinition().inputSchema().contains("subject"));
        }
    }

    @Test
    void requiresReadScopeAndReturnsOnlyHeadersAndAggregateCounts() {
        var requested = new AtomicReference<ImportJobId>();
        var tools = tools(importId -> {
            requested.set(importId);
            return profile();
        });
        authenticate("reader", GovernedActionPlanMcpTools.PROPOSE_SCOPE);
        assertThrows(
                AccessDeniedException.class,
                () -> tools.profileIntakeArtifact(IMPORT_ID.value().toString()));

        authenticate("reader", IntakeProfilingMcpTools.READ_SCOPE);
        var result = tools.profileIntakeArtifact(IMPORT_ID.value().toString());

        assertEquals(IMPORT_ID, requested.get());
        assertEquals(ARTIFACT_ID.value().toString(), result.artifactId());
        assertEquals(2, result.dataRows());
        assertEquals(1, result.columns().getFirst().nonBlankValues());
        assertFalse(result.toString().contains("Synthetic Supplier"));
    }

    @Test
    void publishedContractBindsTheRuntimeNameScopeAndValueFreeOutput() throws Exception {
        String contract;
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream(
                "/contracts/mcp/intake-profiling-tools-v1.json"))) {
            contract = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(contract.contains("\"name\": \"profile_intake_artifact\""));
        assertTrue(contract.contains("\"name\": \"suggest_schema_mapping\""));
        assertTrue(contract.contains("\"name\": \"preview_mapped_records\""));
        assertTrue(contract.contains("\"requiredScope\": \"mdm.supplier.read\""));
        assertTrue(contract.contains("never includes workbook row values"));
    }

    @Test
    void requiresReadScopeForMappingAndReturnsOnlyBoundedAggregates() {
        var tools = tools(ignored -> profile());
        authenticate("proposer", GovernedActionPlanMcpTools.PROPOSE_SCOPE);
        assertThrows(
                AccessDeniedException.class,
                () -> tools.suggestSchemaMapping(IMPORT_ID.value().toString()));

        authenticate("reader", IntakeProfilingMcpTools.READ_SCOPE);
        var suggestion = tools.suggestSchemaMapping(IMPORT_ID.value().toString());
        var preview = tools.previewMappedRecords(
                IMPORT_ID.value().toString(),
                List.of(new IntakeProfilingMcpTools.MappingInput(1, "source_record_id")));

        assertEquals("supplier-column-mapping-v1", suggestion.schemaVersion());
        assertEquals("source_record_id", suggestion.columns().getFirst().targetColumn());
        assertEquals("READY", preview.readiness());
        assertEquals(1, preview.columns().size());
        assertFalse(preview.toString().contains("Synthetic Supplier"));
    }

    private static IntakeProfilingMcpTools tools(
            io.stewardmesh.masterdata.application.port.in.ProfileIntakeArtifact useCase) {
        return new IntakeProfilingMcpTools(useCase, ignored -> suggestion(), ignored -> preview());
    }

    private static IntakeArtifactProfile profile() {
        return new IntakeArtifactProfile(
                IMPORT_ID,
                ARTIFACT_ID,
                new SupplierWorkbookProfile(
                        "1.1.0",
                        "suppliers",
                        2,
                        List.of(new SupplierWorkbookColumnProfile(
                                1, "source_record_id", true, 1, 1, 0))));
    }

    private static IntakeMappingSuggestion suggestion() {
        return new IntakeMappingSuggestion(
                IMPORT_ID,
                ARTIFACT_ID,
                "supplier-column-mapping-v1",
                List.of(new SuggestedColumnMapping(
                        1,
                        "source_record_id",
                        "source_record_id",
                        MappingDecision.EXACT_CANONICAL,
                        10_000)),
                List.of());
    }

    private static MappedRecordsPreview preview() {
        return new MappedRecordsPreview(
                IMPORT_ID,
                ARTIFACT_ID,
                2,
                MappedRecordsReadiness.READY,
                List.of(),
                List.of(new MappedColumnPreview(
                        1, "source_record_id", "source_record_id", true, 2, 0, 0)));
    }

    private static void authenticate(String subject, String scope) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        subject,
                        "ignored",
                        List.of(new SimpleGrantedAuthority("SCOPE_" + scope))));
    }
}
