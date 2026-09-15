package io.stewardmesh.masterdata.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.application.identity.IdentityResolutionCandidatePage;
import io.stewardmesh.masterdata.application.identity.IdentityResolutionKey;
import io.stewardmesh.masterdata.application.intake.SupplierImportStatus;
import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
import io.stewardmesh.masterdata.domain.identity.MatchFeature;
import io.stewardmesh.masterdata.domain.identity.MatchFeatureCode;
import io.stewardmesh.masterdata.domain.identity.MatchOutcome;
import io.stewardmesh.masterdata.domain.identity.MatchRulesetId;
import io.stewardmesh.masterdata.domain.identity.MatchSignal;
import io.stewardmesh.masterdata.domain.intake.ImportCounters;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ImportStatus;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class IdentityResolutionMcpToolsTest {

    private static final UUID IMPORT_ID = UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb10a");
    private static final UUID CANDIDATE_ID = UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb10b");
    private static final IdentityResolutionKey KEY = new IdentityResolutionKey(
            new SourceRecordIdentity(new SourceSystemRef("SYNTHETIC"), "ROW-1", 1),
            new MatchRulesetId("supplier-identity-v1"));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publishesTheFourSupervisorReadCapabilities() {
        var callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(tools())
                .build()
                .getToolCallbacks();

        assertEquals(
                Set.of("get_import_status", "find_party_candidates", "find_site_candidates", "explain_match"),
                Arrays.stream(callbacks)
                        .map(callback -> callback.getToolDefinition().name())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        assertTrue(Arrays.stream(callbacks)
                .allMatch(callback -> callback.getToolDefinition().description().startsWith("READ:")));
    }

    @Test
    void publishedContractNamesEveryRuntimeCapabilityAndScope() throws Exception {
        String contract;
        try (var stream = Objects.requireNonNull(getClass().getResourceAsStream(
                "/contracts/mcp/identity-resolution-tools-v1.json"))) {
            contract = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        for (String tool : Set.of(
                "get_import_status", "find_party_candidates", "find_site_candidates", "explain_match")) {
            assertTrue(contract.contains("\"name\": \"" + tool + "\""));
        }
        assertTrue(contract.contains("\"requiredScope\": \"mdm.supplier.read\""));
    }

    @Test
    void requiresReadScopeAndReturnsBoundedValueFreeEvidence() {
        var tools = tools();
        authenticate(GovernedActionPlanMcpTools.PROPOSE_SCOPE);
        assertThrows(AccessDeniedException.class, () -> tools.getImportStatus(IMPORT_ID.toString()));

        authenticate(GovernedActionPlanMcpTools.READ_SCOPE);
        var status = tools.getImportStatus(IMPORT_ID.toString());
        var candidates = tools.findPartyCandidates("SYNTHETIC", "ROW-1", 1, "supplier-identity-v1");
        var explanation = tools.explainMatch(
                "SYNTHETIC", "ROW-1", 1, "supplier-identity-v1", "party", CANDIDATE_ID.toString());

        assertEquals("MATCHED", status.status());
        assertEquals(1, candidates.totalCandidates());
        assertEquals("INN_EXACT", explanation.features().getFirst().code());
        assertFalse(explanation.toString().contains("Synthetic Supplier"));
    }

    @Test
    void rejectsInvalidIdentityInputBeforeCallingUseCases() {
        authenticate(GovernedActionPlanMcpTools.READ_SCOPE);
        var tools = tools();

        assertThrows(IllegalArgumentException.class, () -> tools.getImportStatus("not-a-uuid"));
        assertThrows(
                IllegalArgumentException.class,
                () -> tools.explainMatch("SYNTHETIC", "ROW-1", 1, "supplier-identity-v1", "address", CANDIDATE_ID.toString()));
    }

    private static IdentityResolutionMcpTools tools() {
        MatchDecision decision = decision();
        return new IdentityResolutionMcpTools(
                ignored -> new SupplierImportStatus(
                        new ImportJobId(IMPORT_ID), ImportStatus.MATCHED,
                        new ImportCounters(1, 1, 0, 0, 0), null),
                query -> new IdentityResolutionCandidatePage(
                        query.key(), query.entityType(), query.page(), query.size(), 1, List.of(decision)),
                ignored -> decision);
    }

    private static MatchDecision decision() {
        return new MatchDecision(
                MatchEntityType.PARTY, CANDIDATE_ID, MatchOutcome.AUTO_LINK, 10_000,
                KEY.rulesetId(), false,
                List.of(new MatchFeature(MatchFeatureCode.INN_EXACT, MatchSignal.MATCH, 10_000)));
    }

    private static void authenticate(String scope) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "synthetic-reader", "ignored",
                        List.of(new SimpleGrantedAuthority("SCOPE_" + scope))));
    }
}
