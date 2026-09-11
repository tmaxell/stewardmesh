package io.stewardmesh.masterdata.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.application.goldenrecord.GoldenRecordProjection;
import io.stewardmesh.masterdata.application.goldenrecord.GoldenRecordWriteException;
import io.stewardmesh.masterdata.application.identity.MatchEvaluation;
import io.stewardmesh.masterdata.application.port.out.ImportJobRepository;
import io.stewardmesh.masterdata.application.port.out.IntakeArtifactRepository;
import io.stewardmesh.masterdata.application.port.out.LoadGoldenRecordProjection;
import io.stewardmesh.masterdata.application.port.out.LoadMatchEvaluation;
import io.stewardmesh.masterdata.application.port.out.SourceRecordWriter;
import io.stewardmesh.masterdata.application.port.out.StoreGoldenRecordProjection;
import io.stewardmesh.masterdata.application.port.out.StoreMatchEvaluation;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttributeName;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenRecordProjector;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenRecordVersion;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenSourceAssertion;
import io.stewardmesh.masterdata.domain.goldenrecord.SourceAssociation;
import io.stewardmesh.masterdata.domain.goldenrecord.SourceAssociationId;
import io.stewardmesh.masterdata.domain.goldenrecord.SourcePriority;
import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
import io.stewardmesh.masterdata.domain.identity.MatchFeature;
import io.stewardmesh.masterdata.domain.identity.MatchFeatureCode;
import io.stewardmesh.masterdata.domain.identity.MatchOutcome;
import io.stewardmesh.masterdata.domain.identity.MatchRulesetId;
import io.stewardmesh.masterdata.domain.identity.MatchSignal;
import io.stewardmesh.masterdata.domain.identity.SupplierSourceNormalizer;
import io.stewardmesh.masterdata.application.identity.IdentityResolutionKey;
import io.stewardmesh.masterdata.domain.intake.ImportJob;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifact;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import io.stewardmesh.masterdata.domain.intake.SourceRecord;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import io.stewardmesh.masterdata.persistence.jpa.IntakePersistenceConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = GoldenRecordPersistenceIT.TestApplication.class)
class GoldenRecordPersistenceIT extends PostgreSqlIntegrationTestSupport {

    private static final Instant INGESTED_AT = Instant.parse("2026-09-11T09:00:00Z");
    private static final Instant PROJECTED_AT = Instant.parse("2026-09-11T10:00:00Z");
    private static final MatchRulesetId MATCH_RULESET = new MatchRulesetId("supplier-match-v1");

    @Autowired
    private IntakeArtifactRepository artifactRepository;

    @Autowired
    private ImportJobRepository importJobRepository;

    @Autowired
    private SourceRecordWriter sourceRecordWriter;

    @Autowired
    private StoreMatchEvaluation matchEvaluationStore;

    @Autowired
    private LoadMatchEvaluation matchEvaluationLoader;

    @Autowired
    private StoreGoldenRecordProjection goldenRecordStore;

    @Autowired
    private LoadGoldenRecordProjection goldenRecordLoader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void roundTripsPartyAddressSiteAndCompleteProvenance() {
        var fixture = persistedFixture("round-trip");
        var projection = fixture.projection(1, PROJECTED_AT);

        goldenRecordStore.save(projection);

        assertEquals(projection.party(), goldenRecordLoader.findParty(fixture.partyId()).orElseThrow());
        assertEquals(
                projection.addresses().getFirst(),
                goldenRecordLoader.findAddress(fixture.addressId()).orElseThrow());
        assertEquals(
                projection.sites().getFirst(),
                goldenRecordLoader.findSite(fixture.siteId()).orElseThrow());
        var legalName = goldenRecordLoader
                .findParty(fixture.partyId())
                .orElseThrow()
                .attributes()
                .get(GoldenAttributeName.LEGAL_NAME);
        assertEquals(fixture.source().identity(), legalName.provenance().sourceRecord());
        assertEquals(fixture.association().id(), legalName.provenance().associationId());
        assertEquals(GoldenRecordProjector.RULESET_ID, legalName.provenance().rulesetId());
    }

    @Test
    void reconstructsPersistedMatchDecisionsAndFeatureEvidence() {
        var fixture = persistedFixture("match-read");

        var evaluation = matchEvaluationLoader.find(
                new IdentityResolutionKey(fixture.source().identity(), MATCH_RULESET)).orElseThrow();

        assertEquals(1, evaluation.partyDecisions().size());
        assertEquals(1, evaluation.siteDecisions().size());
        assertEquals(MatchFeatureCode.INN_EXACT,
                evaluation.partyDecisions().getFirst().features().getFirst().code());
        assertEquals(INGESTED_AT.plusSeconds(1), evaluation.evaluatedAt());
    }

    @Test
    void recalculationAdvancesCurrentMetadataAndPreservesPriorSnapshot() {
        var fixture = persistedFixture("recalculation");
        goldenRecordStore.save(fixture.projection(1, PROJECTED_AT));

        goldenRecordStore.save(fixture.projection(2, PROJECTED_AT.plusSeconds(60)));

        assertEquals(
                new GoldenRecordVersion(2),
                goldenRecordLoader.findParty(fixture.partyId()).orElseThrow().version());
        assertEquals(2, jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM golden_record_version
                WHERE entity_type = 'PARTY' AND entity_id = ?
                """,
                Integer.class,
                fixture.partyId().value()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                """
                SELECT lock_version FROM golden_record_metadata
                WHERE entity_type = 'PARTY' AND entity_id = ?
                """,
                Long.class,
                fixture.partyId().value()));
    }

    @Test
    void rejectsDuplicateProjectionVersionWithoutCreatingAnotherSnapshot() {
        var fixture = persistedFixture("uniqueness");
        var projection = fixture.projection(1, PROJECTED_AT);
        goldenRecordStore.save(projection);

        assertThrows(GoldenRecordWriteException.class, () -> goldenRecordStore.save(projection));

        assertEquals(1, jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM golden_record_version
                WHERE entity_type = 'PARTY' AND entity_id = ?
                """,
                Integer.class,
                fixture.partyId().value()));
    }

    @Test
    void rollsBackValidAssociationAndMetadataWhenLaterAssociationViolatesLineage() {
        var fixture = persistedFixture("rollback");
        var missingIdentity = new SourceRecordIdentity(
                new SourceSystemRef("SYNTHETIC_GOLDEN"), "missing-source", 1);
        var invalidAssociation = association(
                UUID.randomUUID(),
                missingIdentity,
                fixture.partyId(),
                fixture.addressId(),
                fixture.siteId());
        var validProjection = fixture.projection(1, PROJECTED_AT);
        var invalidBatch = new GoldenRecordProjection(
                validProjection.party(),
                validProjection.addresses(),
                validProjection.sites(),
                List.of(fixture.association(), invalidAssociation));

        assertThrows(GoldenRecordWriteException.class, () -> goldenRecordStore.save(invalidBatch));

        assertFalse(goldenRecordLoader.findParty(fixture.partyId()).isPresent());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM source_association WHERE association_id = ?",
                Integer.class,
                fixture.association().id().value()));
    }

    private Fixture persistedFixture(String suffix) {
        UUID random = UUID.randomUUID();
        String checksum = (random.toString().replace("-", "") + "a".repeat(64)).substring(0, 64);
        var artifact = new IntakeArtifact(
                new IntakeArtifactId(random),
                checksum,
                "intake/sha256/" + checksum,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                256,
                INGESTED_AT.minusSeconds(60));
        artifactRepository.save(artifact);
        var job = ImportJob.received(
                new ImportJobId(UUID.randomUUID()),
                artifact.id(),
                new SourceSystemRef("SYNTHETIC_GOLDEN"),
                INGESTED_AT.minusSeconds(30));
        importJobRepository.save(job);
        var source = new SourceRecord(
                new SourceRecordIdentity(job.sourceSystem(), suffix + "-source", 1),
                job.id(),
                INGESTED_AT,
                SupplierSourceNormalizer.RULESET_ID,
                Map.of("legal_name", "Synthetic Golden LLC", "inn", "9902000005"),
                Map.of("legal_name", "SYNTHETIC GOLDEN LLC", "inn", "9902000005"));
        sourceRecordWriter.writeBatch(job.id(), List.of(source), List.of());
        var partyId = new SupplierPartyId(UUID.randomUUID());
        var addressId = new SupplierAddressId(UUID.randomUUID());
        var siteId = new SupplierSiteId(UUID.randomUUID());
        var partyDecision = decision(MatchEntityType.PARTY, partyId.value());
        var siteDecision = decision(MatchEntityType.SITE, siteId.value());
        matchEvaluationStore.save(new MatchEvaluation(
                source.identity(),
                MATCH_RULESET,
                INGESTED_AT.plusSeconds(1),
                List.of(partyDecision),
                List.of(siteDecision)));
        var association = new SourceAssociation(
                new SourceAssociationId(UUID.randomUUID()),
                source.identity(),
                partyId,
                Optional.of(addressId),
                Optional.of(siteId),
                partyDecision,
                Optional.of(siteDecision),
                INGESTED_AT.plusSeconds(1),
                Optional.empty());
        return new Fixture(source, partyId, addressId, siteId, association);
    }

    private static SourceAssociation association(
            UUID associationId,
            SourceRecordIdentity source,
            SupplierPartyId partyId,
            SupplierAddressId addressId,
            SupplierSiteId siteId) {
        return new SourceAssociation(
                new SourceAssociationId(associationId),
                source,
                partyId,
                Optional.of(addressId),
                Optional.of(siteId),
                decision(MatchEntityType.PARTY, partyId.value()),
                Optional.of(decision(MatchEntityType.SITE, siteId.value())),
                INGESTED_AT.plusSeconds(1),
                Optional.empty());
    }

    private static MatchDecision decision(MatchEntityType entityType, UUID candidateId) {
        return new MatchDecision(
                entityType,
                candidateId,
                MatchOutcome.AUTO_LINK,
                10_000,
                MATCH_RULESET,
                false,
                List.of(new MatchFeature(
                        MatchFeatureCode.INN_EXACT, MatchSignal.MATCH, 10_000)));
    }

    private record Fixture(
            SourceRecord source,
            SupplierPartyId partyId,
            SupplierAddressId addressId,
            SupplierSiteId siteId,
            SourceAssociation association) {

        GoldenRecordProjection projection(long version, Instant projectedAt) {
            var values = Map.ofEntries(
                    Map.entry(GoldenAttributeName.LEGAL_NAME, "SYNTHETIC GOLDEN LLC"),
                    Map.entry(GoldenAttributeName.INN, "9902000005"),
                    Map.entry(GoldenAttributeName.COUNTRY_CODE, "RU"),
                    Map.entry(GoldenAttributeName.CITY, "TEST CITY"),
                    Map.entry(GoldenAttributeName.ADDRESS_LINE, "1 TEST STREET"),
                    Map.entry(GoldenAttributeName.KPP, "990201001"),
                    Map.entry(GoldenAttributeName.SITE_CODE, "SITE-A"));
            var assertion = new GoldenSourceAssertion(
                    source.identity(),
                    source.ingestedAt(),
                    new SourcePriority(500),
                    association,
                    values);
            var projector = new GoldenRecordProjector();
            var recordVersion = new GoldenRecordVersion(version);
            return new GoldenRecordProjection(
                    projector.projectParty(partyId, recordVersion, projectedAt, List.of(assertion)),
                    List.of(projector.projectAddress(
                            addressId, partyId, recordVersion, projectedAt, List.of(assertion))),
                    List.of(projector.projectSite(
                            siteId,
                            partyId,
                            addressId,
                            recordVersion,
                            projectedAt,
                            List.of(assertion))),
                    List.of(association));
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(IntakePersistenceConfiguration.class)
    static class TestApplication {}
}
