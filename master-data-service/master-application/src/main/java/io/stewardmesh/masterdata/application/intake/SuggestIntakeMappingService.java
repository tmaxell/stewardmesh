package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.application.port.in.ProfileIntakeArtifact;
import io.stewardmesh.masterdata.application.port.in.SuggestIntakeMapping;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Suggests only exact canonical names and a closed, versioned alias set. */
public final class SuggestIntakeMappingService implements SuggestIntakeMapping {

    private final ProfileIntakeArtifact profiles;

    public SuggestIntakeMappingService(ProfileIntakeArtifact profiles) {
        this.profiles = Objects.requireNonNull(profiles, "profiles must not be null");
    }

    @Override
    public IntakeMappingSuggestion execute(ImportJobId importJobId) {
        IntakeArtifactProfile profile = profiles.execute(importJobId);
        Map<String, Long> targetCounts = profile.workbook().columns().stream()
                .map(column -> SupplierColumnMappingPolicy.target(column.header()))
                .flatMap(java.util.Optional::stream)
                .collect(Collectors.groupingBy(
                        SupplierColumnMappingPolicy.Target::column, Collectors.counting()));
        List<SuggestedColumnMapping> suggestions = profile.workbook().columns().stream()
                .map(column -> SupplierColumnMappingPolicy.target(column.header())
                        .map(target -> suggestion(column, target, targetCounts.get(target.column()) > 1))
                        .orElseGet(() -> new SuggestedColumnMapping(
                                column.position(), column.header(), null,
                                MappingDecision.UNMAPPED, 0)))
                .toList();
        Set<String> mapped = suggestions.stream()
                .filter(value -> value.decision() != MappingDecision.TARGET_CONFLICT)
                .map(SuggestedColumnMapping::target)
                .flatMap(java.util.Optional::stream)
                .collect(Collectors.toCollection(HashSet::new));
        List<String> missing = SupplierColumnMappingPolicy.CANONICAL_COLUMNS.stream()
                .filter(SupplierColumnMappingPolicy::required)
                .filter(column -> !mapped.contains(column))
                .toList();
        return new IntakeMappingSuggestion(
                profile.importJobId(),
                profile.artifactId(),
                SupplierColumnMappingPolicy.SCHEMA_VERSION,
                suggestions,
                missing);
    }

    private static SuggestedColumnMapping suggestion(
            SupplierWorkbookColumnProfile source,
            SupplierColumnMappingPolicy.Target target,
            boolean conflict) {
        return new SuggestedColumnMapping(
                source.position(),
                source.header(),
                target.column(),
                conflict ? MappingDecision.TARGET_CONFLICT : target.decision(),
                conflict ? 0 : target.confidenceBasisPoints());
    }
}
