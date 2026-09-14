package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.application.port.in.PreviewMappedRecords;
import io.stewardmesh.masterdata.application.port.in.ProfileIntakeArtifact;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Validates a selected mapping and previews only aggregate completeness, never row values. */
public final class PreviewMappedRecordsService implements PreviewMappedRecords {

    private final ProfileIntakeArtifact profiles;

    public PreviewMappedRecordsService(ProfileIntakeArtifact profiles) {
        this.profiles = Objects.requireNonNull(profiles, "profiles must not be null");
    }

    @Override
    public MappedRecordsPreview execute(PreviewMappedRecordsCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        IntakeArtifactProfile profile = profiles.execute(command.importJobId());
        Map<Integer, SupplierWorkbookColumnProfile> sourceColumns = profile.workbook().columns().stream()
                .collect(Collectors.toUnmodifiableMap(SupplierWorkbookColumnProfile::position, Function.identity()));
        requireUnique(command.mappings().stream().map(ColumnMapping::sourcePosition).toList(), "MAPPING_SOURCE_DUPLICATE");
        requireUnique(command.mappings().stream().map(ColumnMapping::targetColumn).toList(), "MAPPING_TARGET_DUPLICATE");

        List<MappedColumnPreview> columns = command.mappings().stream().map(mapping -> {
            SupplierWorkbookColumnProfile source = sourceColumns.get(mapping.sourcePosition());
            if (source == null) {
                throw failure("MAPPING_SOURCE_UNKNOWN", "mapping references an unknown source position");
            }
            if (!SupplierColumnMappingPolicy.canonical(mapping.targetColumn())) {
                throw failure("MAPPING_TARGET_UNKNOWN", "mapping references an unknown canonical target");
            }
            return new MappedColumnPreview(
                    source.position(),
                    source.header(),
                    mapping.targetColumn(),
                    SupplierColumnMappingPolicy.required(mapping.targetColumn()),
                    source.nonBlankValues(),
                    source.blankValues(),
                    source.formulaCells());
        }).toList();
        Set<String> mappedTargets = columns.stream()
                .map(MappedColumnPreview::targetColumn)
                .collect(Collectors.toCollection(HashSet::new));
        List<String> missing = SupplierColumnMappingPolicy.CANONICAL_COLUMNS.stream()
                .filter(SupplierColumnMappingPolicy::required)
                .filter(target -> !mappedTargets.contains(target))
                .toList();
        boolean formulas = columns.stream().anyMatch(column -> column.formulaCells() > 0);
        boolean incomplete = !missing.isEmpty()
                || columns.stream().anyMatch(column -> column.required() && column.blankValues() > 0);
        MappedRecordsReadiness readiness = formulas
                ? MappedRecordsReadiness.FORMULAS_PRESENT
                : incomplete
                        ? MappedRecordsReadiness.INCOMPLETE_REQUIRED_COLUMNS
                        : MappedRecordsReadiness.READY;
        return new MappedRecordsPreview(
                profile.importJobId(),
                profile.artifactId(),
                profile.workbook().dataRows(),
                readiness,
                missing,
                columns);
    }

    private static void requireUnique(List<?> values, String code) {
        Map<Object, Integer> counts = new HashMap<>();
        values.forEach(value -> counts.merge(value, 1, Integer::sum));
        if (counts.values().stream().anyMatch(count -> count > 1)) {
            throw failure(code, "mapping values must be unique");
        }
    }

    private static IntakeMappingException failure(String code, String message) {
        return new IntakeMappingException(code, message);
    }
}
