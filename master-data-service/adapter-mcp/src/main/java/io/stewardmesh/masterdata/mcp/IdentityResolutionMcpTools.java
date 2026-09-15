package io.stewardmesh.masterdata.mcp;

import io.stewardmesh.masterdata.application.identity.IdentityResolutionCandidatePage;
import io.stewardmesh.masterdata.application.identity.IdentityResolutionCandidateQuery;
import io.stewardmesh.masterdata.application.identity.IdentityResolutionKey;
import io.stewardmesh.masterdata.application.identity.MatchExplanationQuery;
import io.stewardmesh.masterdata.application.intake.SupplierImportStatus;
import io.stewardmesh.masterdata.application.port.in.GetMatchExplanation;
import io.stewardmesh.masterdata.application.port.in.GetSupplierImportStatus;
import io.stewardmesh.masterdata.application.port.in.ListIdentityResolutionCandidates;
import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
import io.stewardmesh.masterdata.domain.identity.MatchFeature;
import io.stewardmesh.masterdata.domain.identity.MatchRulesetId;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Bounded, value-free MCP reads used by the reference supervisor's identification phase. */
public final class IdentityResolutionMcpTools {

    static final int DEFAULT_PAGE_SIZE = 20;

    private final GetSupplierImportStatus imports;
    private final ListIdentityResolutionCandidates candidates;
    private final GetMatchExplanation explanations;
    private final McpCallerContext callers;

    public IdentityResolutionMcpTools(
            GetSupplierImportStatus imports,
            ListIdentityResolutionCandidates candidates,
            GetMatchExplanation explanations) {
        this.imports = Objects.requireNonNull(imports, "imports must not be null");
        this.candidates = Objects.requireNonNull(candidates, "candidates must not be null");
        this.explanations = Objects.requireNonNull(explanations, "explanations must not be null");
        this.callers = new McpCallerContext();
    }

    @Tool(name = "get_import_status", description = "READ: get bounded supplier-import progress and counters.")
    public ImportStatusResult getImportStatus(
            @ToolParam(description = "Supplier import UUID") String importId) {
        callers.requireScope(GovernedActionPlanMcpTools.READ_SCOPE);
        return ImportStatusResult.from(imports.execute(new ImportJobId(uuid(importId, "importId"))));
    }

    @Tool(name = "find_party_candidates", description = "READ: list bounded party candidates without supplier values.")
    public CandidatePageResult findPartyCandidates(
            @ToolParam(description = "Source system code") String sourceSystem,
            @ToolParam(description = "Source record identifier") String sourceRecordId,
            @ToolParam(description = "Positive source version") long sourceVersion,
            @ToolParam(description = "Versioned matching ruleset identifier") String rulesetId) {
        return find(sourceSystem, sourceRecordId, sourceVersion, rulesetId, MatchEntityType.PARTY);
    }

    @Tool(name = "find_site_candidates", description = "READ: list bounded supplier-site candidates without supplier values.")
    public CandidatePageResult findSiteCandidates(
            @ToolParam(description = "Source system code") String sourceSystem,
            @ToolParam(description = "Source record identifier") String sourceRecordId,
            @ToolParam(description = "Positive source version") long sourceVersion,
            @ToolParam(description = "Versioned matching ruleset identifier") String rulesetId) {
        return find(sourceSystem, sourceRecordId, sourceVersion, rulesetId, MatchEntityType.SITE);
    }

    @Tool(name = "explain_match", description = "READ: return feature-level evidence for one candidate without raw values.")
    public CandidateResult explainMatch(
            @ToolParam(description = "Source system code") String sourceSystem,
            @ToolParam(description = "Source record identifier") String sourceRecordId,
            @ToolParam(description = "Positive source version") long sourceVersion,
            @ToolParam(description = "Versioned matching ruleset identifier") String rulesetId,
            @ToolParam(description = "PARTY or SITE") String entityType,
            @ToolParam(description = "Candidate UUID") String candidateId) {
        callers.requireScope(GovernedActionPlanMcpTools.READ_SCOPE);
        var query = new MatchExplanationQuery(
                key(sourceSystem, sourceRecordId, sourceVersion, rulesetId),
                entityType(entityType), uuid(candidateId, "candidateId"));
        return CandidateResult.from(explanations.execute(query), true);
    }

    private CandidatePageResult find(
            String sourceSystem,
            String sourceRecordId,
            long sourceVersion,
            String rulesetId,
            MatchEntityType entityType) {
        callers.requireScope(GovernedActionPlanMcpTools.READ_SCOPE);
        var query = new IdentityResolutionCandidateQuery(
                key(sourceSystem, sourceRecordId, sourceVersion, rulesetId),
                entityType, 0, DEFAULT_PAGE_SIZE);
        return CandidatePageResult.from(candidates.execute(query));
    }

    private static IdentityResolutionKey key(
            String sourceSystem, String sourceRecordId, long sourceVersion, String rulesetId) {
        return new IdentityResolutionKey(
                new SourceRecordIdentity(
                        new SourceSystemRef(required(sourceSystem, "sourceSystem")),
                        required(sourceRecordId, "sourceRecordId"), sourceVersion),
                new MatchRulesetId(required(rulesetId, "rulesetId")));
    }

    private static MatchEntityType entityType(String value) {
        try {
            return MatchEntityType.valueOf(required(value, "entityType").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("entityType must be PARTY or SITE", exception);
        }
    }

    private static UUID uuid(String value, String field) {
        try {
            return UUID.fromString(required(value, field));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " must be a UUID", exception);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    public record ImportStatusResult(
            String importId,
            String status,
            int totalRows,
            int acceptedRows,
            int rejectedRows,
            int warningCount,
            int errorCount,
            String failureCode) {

        static ImportStatusResult from(SupplierImportStatus status) {
            var counters = status.counters();
            return new ImportStatusResult(
                    status.importJobId().value().toString(), status.status().name(),
                    counters.totalRows(), counters.acceptedRows(), counters.rejectedRows(),
                    counters.warningCount(), counters.errorCount(), status.failureCode());
        }
    }

    public record CandidatePageResult(
            String sourceSystem,
            String sourceRecordId,
            long sourceVersion,
            String rulesetId,
            String entityType,
            int totalCandidates,
            List<CandidateResult> candidates) {

        static CandidatePageResult from(IdentityResolutionCandidatePage page) {
            var source = page.key().sourceRecordIdentity();
            return new CandidatePageResult(
                    source.originSystem().value(), source.sourceRecordId(), source.sourceVersion(),
                    page.key().rulesetId().value(), page.entityType().name(), page.totalCandidates(),
                    page.candidates().stream().map(value -> CandidateResult.from(value, false)).toList());
        }
    }

    public record CandidateResult(
            String entityType,
            String candidateId,
            String outcome,
            int scoreBasisPoints,
            String rulesetId,
            boolean hardConflict,
            List<FeatureResult> features) {

        static CandidateResult from(MatchDecision decision, boolean includeFeatures) {
            return new CandidateResult(
                    decision.entityType().name(), decision.candidateId().toString(),
                    decision.outcome().name(), decision.scoreBasisPoints(),
                    decision.rulesetId().value(), decision.hardConflict(),
                    includeFeatures
                            ? decision.features().stream().map(FeatureResult::from).toList()
                            : List.of());
        }
    }

    public record FeatureResult(String code, String signal, int contributionBasisPoints) {

        static FeatureResult from(MatchFeature feature) {
            return new FeatureResult(
                    feature.code().name(), feature.signal().name(), feature.contributionBasisPoints());
        }
    }
}
