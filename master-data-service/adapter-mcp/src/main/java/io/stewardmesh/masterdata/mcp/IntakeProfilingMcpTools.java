package io.stewardmesh.masterdata.mcp;

import io.stewardmesh.masterdata.application.intake.IntakeArtifactProfile;
import io.stewardmesh.masterdata.application.intake.IntakeMappingSuggestion;
import io.stewardmesh.masterdata.application.intake.MappedColumnPreview;
import io.stewardmesh.masterdata.application.intake.MappedRecordsPreview;
import io.stewardmesh.masterdata.application.intake.PreviewMappedRecordsCommand;
import io.stewardmesh.masterdata.application.intake.ColumnMapping;
import io.stewardmesh.masterdata.application.intake.SupplierWorkbookColumnProfile;
import io.stewardmesh.masterdata.application.port.in.ProfileIntakeArtifact;
import io.stewardmesh.masterdata.application.port.in.PreviewMappedRecords;
import io.stewardmesh.masterdata.application.port.in.SuggestIntakeMapping;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Read-only MCP view of bounded intake structure and aggregate completeness. */
public final class IntakeProfilingMcpTools {

    static final String READ_SCOPE = GovernedActionPlanMcpTools.READ_SCOPE;

    private final ProfileIntakeArtifact profileIntakeArtifact;
    private final SuggestIntakeMapping suggestIntakeMapping;
    private final PreviewMappedRecords previewMappedRecords;
    private final McpCallerContext callers;

    public IntakeProfilingMcpTools(
            ProfileIntakeArtifact profileIntakeArtifact,
            SuggestIntakeMapping suggestIntakeMapping,
            PreviewMappedRecords previewMappedRecords) {
        this.profileIntakeArtifact = Objects.requireNonNull(
                profileIntakeArtifact, "profileIntakeArtifact must not be null");
        this.suggestIntakeMapping = Objects.requireNonNull(
                suggestIntakeMapping, "suggestIntakeMapping must not be null");
        this.previewMappedRecords = Objects.requireNonNull(
                previewMappedRecords, "previewMappedRecords must not be null");
        this.callers = new McpCallerContext();
    }

    @Tool(
            name = "profile_intake_artifact",
            description = "READ: profile workbook structure and aggregate completeness without returning row values.")
    public ProfileResult profileIntakeArtifact(
            @ToolParam(description = "Supplier import UUID") String importId) {
        callers.requireScope(READ_SCOPE);
        IntakeArtifactProfile profile = profileIntakeArtifact.execute(
                new ImportJobId(parseUuid(importId)));
        return ProfileResult.from(profile);
    }

    @Tool(
            name = "suggest_schema_mapping",
            description = "READ: suggest deterministic canonical mappings from headers without reading row values.")
    public MappingSuggestionResult suggestSchemaMapping(
            @ToolParam(description = "Supplier import UUID") String importId) {
        callers.requireScope(READ_SCOPE);
        return MappingSuggestionResult.from(suggestIntakeMapping.execute(new ImportJobId(parseUuid(importId))));
    }

    @Tool(
            name = "preview_mapped_records",
            description = "READ: validate selected mappings and preview aggregate readiness without row values.")
    public MappedRecordsPreviewResult previewMappedRecords(
            @ToolParam(description = "Supplier import UUID") String importId,
            @ToolParam(description = "Explicit source-position to canonical-target mappings")
                    List<MappingInput> mappings) {
        callers.requireScope(READ_SCOPE);
        List<ColumnMapping> selected = Objects.requireNonNull(mappings, "mappings must not be null").stream()
                .map(mapping -> new ColumnMapping(mapping.sourcePosition(), mapping.targetColumn()))
                .toList();
        return MappedRecordsPreviewResult.from(previewMappedRecords.execute(
                new PreviewMappedRecordsCommand(new ImportJobId(parseUuid(importId)), selected)));
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(Objects.requireNonNull(value, "importId must not be null"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("importId must be a UUID", exception);
        }
    }

    public record ProfileResult(
            String importId,
            String artifactId,
            String contractVersion,
            String sheetName,
            int dataRows,
            List<ColumnResult> columns) {

        private static ProfileResult from(IntakeArtifactProfile profile) {
            return new ProfileResult(
                    profile.importJobId().value().toString(),
                    profile.artifactId().value().toString(),
                    profile.workbook().contractVersion(),
                    profile.workbook().sheetName(),
                    profile.workbook().dataRows(),
                    profile.workbook().columns().stream().map(ColumnResult::from).toList());
        }
    }

    public record ColumnResult(
            int position,
            String header,
            boolean canonical,
            int nonBlankValues,
            int blankValues,
            int formulaCells) {

        private static ColumnResult from(SupplierWorkbookColumnProfile column) {
            return new ColumnResult(
                    column.position(), column.header(), column.canonical(),
                    column.nonBlankValues(), column.blankValues(), column.formulaCells());
        }
    }

    public record MappingInput(int sourcePosition, String targetColumn) {}

    public record MappingSuggestionResult(
            String importId,
            String artifactId,
            String schemaVersion,
            List<SuggestedMappingResult> columns,
            List<String> missingRequiredColumns) {

        private static MappingSuggestionResult from(IntakeMappingSuggestion suggestion) {
            return new MappingSuggestionResult(
                    suggestion.importJobId().value().toString(),
                    suggestion.artifactId().value().toString(),
                    suggestion.schemaVersion(),
                    suggestion.columns().stream()
                            .map(value -> new SuggestedMappingResult(
                                    value.sourcePosition(),
                                    value.sourceHeader(),
                                    value.targetColumn(),
                                    value.decision().name(),
                                    value.confidenceBasisPoints()))
                            .toList(),
                    suggestion.missingRequiredColumns());
        }
    }

    public record SuggestedMappingResult(
            int sourcePosition,
            String sourceHeader,
            String targetColumn,
            String decision,
            int confidenceBasisPoints) {}

    public record MappedRecordsPreviewResult(
            String importId,
            String artifactId,
            int dataRows,
            String readiness,
            List<String> missingRequiredColumns,
            List<MappedColumnResult> columns) {

        private static MappedRecordsPreviewResult from(MappedRecordsPreview preview) {
            return new MappedRecordsPreviewResult(
                    preview.importJobId().value().toString(),
                    preview.artifactId().value().toString(),
                    preview.dataRows(),
                    preview.readiness().name(),
                    preview.missingRequiredColumns(),
                    preview.columns().stream().map(MappedColumnResult::from).toList());
        }
    }

    public record MappedColumnResult(
            int sourcePosition,
            String sourceHeader,
            String targetColumn,
            boolean required,
            int nonBlankValues,
            int blankValues,
            int formulaCells) {

        private static MappedColumnResult from(MappedColumnPreview preview) {
            return new MappedColumnResult(
                    preview.sourcePosition(),
                    preview.sourceHeader(),
                    preview.targetColumn(),
                    preview.required(),
                    preview.nonBlankValues(),
                    preview.blankValues(),
                    preview.formulaCells());
        }
    }
}
