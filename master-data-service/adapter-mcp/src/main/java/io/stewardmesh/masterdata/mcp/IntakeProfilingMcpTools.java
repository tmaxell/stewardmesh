package io.stewardmesh.masterdata.mcp;

import io.stewardmesh.masterdata.application.intake.IntakeArtifactProfile;
import io.stewardmesh.masterdata.application.intake.SupplierWorkbookColumnProfile;
import io.stewardmesh.masterdata.application.port.in.ProfileIntakeArtifact;
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
    private final McpCallerContext callers;

    public IntakeProfilingMcpTools(ProfileIntakeArtifact profileIntakeArtifact) {
        this.profileIntakeArtifact = Objects.requireNonNull(
                profileIntakeArtifact, "profileIntakeArtifact must not be null");
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
}
