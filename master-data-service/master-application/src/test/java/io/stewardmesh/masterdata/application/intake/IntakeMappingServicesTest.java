package io.stewardmesh.masterdata.application.intake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntakeMappingServicesTest {

    private static final ImportJobId IMPORT_ID = new ImportJobId(
            UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb10a"));
    private static final IntakeArtifactId ARTIFACT_ID = new IntakeArtifactId(
            UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb10b"));

    @Test
    void suggestsExactAndKnownAliasMappingsWithoutInspectingRowValues() {
        var service = new SuggestIntakeMappingService(ignored -> profile(List.of(
                column(1, "source_record_id"),
                column(2, "version"),
                column(3, "company_name"),
                column(4, "tax_id"),
                column(5, "Country Code"),
                column(6, "city"),
                column(7, "address"),
                column(8, "instructions_for_agent"))));

        IntakeMappingSuggestion result = service.execute(IMPORT_ID);

        assertEquals("supplier-column-mapping-v1", result.schemaVersion());
        assertTrue(result.missingRequiredColumns().isEmpty());
        assertEquals(MappingDecision.EXACT_CANONICAL, result.columns().get(0).decision());
        assertEquals(MappingDecision.KNOWN_ALIAS, result.columns().get(1).decision());
        assertEquals("source_version", result.columns().get(1).target().orElseThrow());
        assertEquals(MappingDecision.UNMAPPED, result.columns().get(7).decision());
        assertTrue(result.columns().get(7).target().isEmpty());
    }

    @Test
    void flagsAllAmbiguousTargetsAndReportsTheRequiredGap() {
        var service = new SuggestIntakeMappingService(ignored -> profile(List.of(
                column(1, "company_name"),
                column(2, "supplier_name"))));

        IntakeMappingSuggestion result = service.execute(IMPORT_ID);

        assertEquals(
                List.of(MappingDecision.TARGET_CONFLICT, MappingDecision.TARGET_CONFLICT),
                result.columns().stream().map(SuggestedColumnMapping::decision).toList());
        assertTrue(result.missingRequiredColumns().contains("legal_name"));
    }

    @Test
    void previewsAggregateReadinessForAnExplicitMapping() {
        List<SupplierWorkbookColumnProfile> source = List.of(
                column(1, "record_id"),
                column(2, "version"),
                column(3, "supplier_name"),
                column(4, "tax_id"),
                column(5, "country"),
                column(6, "town"),
                column(7, "address"));
        var service = new PreviewMappedRecordsService(ignored -> profile(source));

        MappedRecordsPreview result = service.execute(new PreviewMappedRecordsCommand(
                IMPORT_ID,
                List.of(
                        mapping(1, "source_record_id"),
                        mapping(2, "source_version"),
                        mapping(3, "legal_name"),
                        mapping(4, "inn"),
                        mapping(5, "country_code"),
                        mapping(6, "city"),
                        mapping(7, "address_line"))));

        assertEquals(MappedRecordsReadiness.READY, result.readiness());
        assertTrue(result.missingRequiredColumns().isEmpty());
        assertEquals(2, result.dataRows());
        assertEquals(7, result.columns().size());
    }

    @Test
    void reportsMissingRequiredValuesAndFormulaRiskConservatively() {
        var incomplete = new PreviewMappedRecordsService(ignored -> profile(List.of(
                column(1, "source_record_id"),
                new SupplierWorkbookColumnProfile(2, "legal_name", true, 1, 1, 0))));
        MappedRecordsPreview missing = incomplete.execute(new PreviewMappedRecordsCommand(
                IMPORT_ID,
                List.of(mapping(1, "source_record_id"), mapping(2, "legal_name"))));
        assertEquals(MappedRecordsReadiness.INCOMPLETE_REQUIRED_COLUMNS, missing.readiness());
        assertTrue(missing.missingRequiredColumns().contains("inn"));

        var formulas = new PreviewMappedRecordsService(ignored -> profile(List.of(
                new SupplierWorkbookColumnProfile(1, "source_record_id", true, 2, 0, 1))));
        MappedRecordsPreview unsafe = formulas.execute(new PreviewMappedRecordsCommand(
                IMPORT_ID, List.of(mapping(1, "source_record_id"))));
        assertEquals(MappedRecordsReadiness.FORMULAS_PRESENT, unsafe.readiness());
    }

    @Test
    void rejectsDuplicateOrUnknownMappingReferences() {
        var service = new PreviewMappedRecordsService(ignored -> profile(List.of(
                column(1, "record_id"), column(2, "supplier_name"))));

        assertCode(
                "MAPPING_SOURCE_DUPLICATE",
                service,
                List.of(mapping(1, "source_record_id"), mapping(1, "legal_name")));
        assertCode(
                "MAPPING_TARGET_DUPLICATE",
                service,
                List.of(mapping(1, "legal_name"), mapping(2, "legal_name")));
        assertCode("MAPPING_SOURCE_UNKNOWN", service, List.of(mapping(9, "legal_name")));
        assertCode("MAPPING_TARGET_UNKNOWN", service, List.of(mapping(1, "unknown")));
    }

    private static void assertCode(
            String code, PreviewMappedRecordsService service, List<ColumnMapping> mappings) {
        IntakeMappingException exception = assertThrows(
                IntakeMappingException.class,
                () -> service.execute(new PreviewMappedRecordsCommand(IMPORT_ID, mappings)));
        assertEquals(code, exception.code());
    }

    private static ColumnMapping mapping(int sourcePosition, String target) {
        return new ColumnMapping(sourcePosition, target);
    }

    private static SupplierWorkbookColumnProfile column(int position, String header) {
        return new SupplierWorkbookColumnProfile(position, header, false, 2, 0, 0);
    }

    private static IntakeArtifactProfile profile(List<SupplierWorkbookColumnProfile> columns) {
        return new IntakeArtifactProfile(
                IMPORT_ID,
                ARTIFACT_ID,
                new SupplierWorkbookProfile("1.1.0", "suppliers", 2, columns));
    }
}
