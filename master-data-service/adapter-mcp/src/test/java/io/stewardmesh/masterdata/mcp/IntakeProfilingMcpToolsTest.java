package io.stewardmesh.masterdata.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.application.intake.IntakeArtifactProfile;
import io.stewardmesh.masterdata.application.intake.SupplierWorkbookColumnProfile;
import io.stewardmesh.masterdata.application.intake.SupplierWorkbookProfile;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
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
    void publishesOneReadOnlyValueFreeToolSchema() {
        var provider = MethodToolCallbackProvider.builder()
                .toolObjects(tools(ignored -> profile()))
                .build();
        var callbacks = provider.getToolCallbacks();

        assertEquals(1, callbacks.length);
        assertEquals("profile_intake_artifact", callbacks[0].getToolDefinition().name());
        assertTrue(callbacks[0].getToolDefinition().description().startsWith("READ:"));
        assertTrue(callbacks[0].getToolDefinition().inputSchema().contains("importId"));
        assertFalse(callbacks[0].getToolDefinition().inputSchema().contains("subject"));
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
        assertTrue(contract.contains("\"requiredScope\": \"mdm.supplier.read\""));
        assertTrue(contract.contains("never includes workbook row values"));
    }

    private static IntakeProfilingMcpTools tools(
            io.stewardmesh.masterdata.application.port.in.ProfileIntakeArtifact useCase) {
        return new IntakeProfilingMcpTools(useCase);
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

    private static void authenticate(String subject, String scope) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        subject,
                        "ignored",
                        List.of(new SimpleGrantedAuthority("SCOPE_" + scope))));
    }
}
