package io.stewardmesh.masterdata.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.MeterRegistry;
import io.stewardmesh.masterdata.application.goldenrecord.GoldenRecordProjection;
import io.stewardmesh.masterdata.application.identity.IdentityResolutionKey;
import io.stewardmesh.masterdata.application.identity.IdentityResolutionStatus;
import io.stewardmesh.masterdata.application.identity.RouteSupplierImportMatchesCommand;
import io.stewardmesh.masterdata.application.port.in.GetIdentityResolutionStatus;
import io.stewardmesh.masterdata.application.port.in.RouteSupplierImportMatches;
import io.stewardmesh.masterdata.application.port.out.ImportJobRepository;
import io.stewardmesh.masterdata.application.port.out.IntakeArtifactRepository;
import io.stewardmesh.masterdata.application.port.out.LoadMatchEvaluation;
import io.stewardmesh.masterdata.application.port.out.SourceRecordWriter;
import io.stewardmesh.masterdata.application.port.out.StoreGoldenRecordProjection;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttributeName;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenRecordProjector;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenRecordVersion;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenSourceAssertion;
import io.stewardmesh.masterdata.domain.goldenrecord.SourceAssociation;
import io.stewardmesh.masterdata.domain.goldenrecord.SourceAssociationId;
import io.stewardmesh.masterdata.domain.goldenrecord.SourcePriority;
import io.stewardmesh.masterdata.domain.identity.MatchOutcome;
import io.stewardmesh.masterdata.domain.identity.SupplierMatchScorer;
import io.stewardmesh.masterdata.domain.identity.SupplierSourceNormalizer;
import io.stewardmesh.masterdata.domain.intake.ImportCounters;
import io.stewardmesh.masterdata.domain.intake.ImportJob;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ImportStatus;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifact;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import io.stewardmesh.masterdata.domain.intake.SourceRecord;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class IdentityResolutionEndToEndIT {

    private static final Instant INGESTED_AT = Instant.parse("2026-09-11T08:00:00Z");
    private static final SupplierPartyId EXACT_PARTY = party("10000000-0000-0000-0000-000000000001");
    private static final SupplierSiteId EXACT_SITE = site("20000000-0000-0000-0000-000000000001");
    private static final SupplierPartyId FUZZY_PARTY = party("10000000-0000-0000-0000-000000000002");
    private static final SupplierPartyId CONFLICT_PARTY = party("10000000-0000-0000-0000-000000000003");
    private static final SupplierPartyId NEW_SITE_PARTY = party("10000000-0000-0000-0000-000000000004");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private IntakeArtifactRepository artifacts;

    @Autowired
    private ImportJobRepository imports;

    @Autowired
    private SourceRecordWriter sources;

    @Autowired
    private RouteSupplierImportMatches routeMatches;

    @Autowired
    private GetIdentityResolutionStatus statuses;

    @Autowired
    private LoadMatchEvaluation evaluations;

    @Autowired
    private StoreGoldenRecordProjection goldenRecords;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meters;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void provesFiveSyntheticOutcomesRerunSafetyGoldenReadsAndLatency() throws Exception {
        seedMatchIndex();
        Scenario newSupplier = scenario(1, "NEW_SUPPLIER", values(
                "9905000008", "1027700132200", "SYNTHETIC NEW LLC", "990501001", "NEW", "NEW STREET"));
        Scenario exact = scenario(2, "EXACT_DUPLICATE", values(
                "9902000005", "1027700132195", "SYNTHETIC EXACT LLC", "990201001", "EXACT", "EXACT STREET"));
        Scenario fuzzy = scenario(3, "FUZZY_REVIEW", values(
                "9903000006", null, "SYNTHETIC ALPHA TRADING", "990301001", "FUZZY", "FUZZY STREET"));
        Scenario conflict = scenario(4, "IDENTIFIER_CONFLICT", values(
                "9904000007", "1027700132196", "SYNTHETIC CONFLICT LLC", "990401001", "CONFLICT", "CONFLICT STREET"));
        Scenario newSite = scenario(5, "EXISTING_PARTY_NEW_SITE", values(
                "9906000009", "1027700132197", "SYNTHETIC MULTISITE LLC", "990602002", "NEW-SITE", "SECOND STREET"));

        long startedAt = System.nanoTime();
        List.of(newSupplier, exact, fuzzy, conflict, newSite)
                .forEach(value -> routeMatches.execute(new RouteSupplierImportMatchesCommand(value.jobId(), 20)));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        IdentityResolutionStatus newStatus = resolutionStatus(newSupplier);
        IdentityResolutionStatus exactStatus = resolutionStatus(exact);
        IdentityResolutionStatus fuzzyStatus = resolutionStatus(fuzzy);
        IdentityResolutionStatus conflictStatus = resolutionStatus(conflict);
        IdentityResolutionStatus newSiteStatus = resolutionStatus(newSite);
        assertEquals(0, newStatus.partyCandidates());
        assertEquals(0, newStatus.siteCandidates());
        assertEquals(2, exactStatus.autoLinks());
        assertEquals(1, fuzzyStatus.reviews());
        assertEquals(1, conflictStatus.reviews());
        assertTrue(conflictStatus.hardConflict());
        assertEquals(1, newSiteStatus.autoLinks());
        assertEquals(0, newSiteStatus.siteCandidates());
        assertEquals(ImportStatus.MATCHED, imports.findById(exact.jobId()).orElseThrow().status());
        assertEquals(ImportStatus.REVIEW_REQUIRED, imports.findById(fuzzy.jobId()).orElseThrow().status());
        assertEquals(2, count("SELECT COUNT(*) FROM stewardship_case"));
        assertTrue(elapsed.compareTo(Duration.ofSeconds(10)) < 0, () -> "five matches took " + elapsed);

        long evaluationsBefore = count("SELECT COUNT(*) FROM match_evaluation");
        long decisionsBefore = count("SELECT COUNT(*) FROM match_decision");
        long reviewsBefore = count("SELECT COUNT(*) FROM stewardship_case");
        routeMatches.execute(new RouteSupplierImportMatchesCommand(exact.jobId(), 20));
        assertEquals(evaluationsBefore, count("SELECT COUNT(*) FROM match_evaluation"));
        assertEquals(decisionsBefore, count("SELECT COUNT(*) FROM match_decision"));
        assertEquals(reviewsBefore, count("SELECT COUNT(*) FROM stewardship_case"));

        projectExactGoldenRecord(exact);
        mockMvc.perform(get(
                                "/api/v1/identity-resolution/sources/{origin}/{record}/versions/{version}",
                                exact.source().identity().originSystem().value(),
                                exact.source().identity().sourceRecordId(),
                                exact.source().identity().sourceVersion())
                        .param("rulesetId", SupplierMatchScorer.RULESET.id().value())
                        .with(identityReadJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoLinks").value(2));
        mockMvc.perform(get("/api/v1/golden-records/PARTY/{partyId}", EXACT_PARTY.value())
                        .with(identityReadJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.attributes[?(@.name == 'LEGAL_NAME')].value")
                        .value("SYNTHETIC EXACT LLC"))
                .andExpect(jsonPath("$.attributes[*].provenance.rulesetId")
                        .value(org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.equalTo("supplier-survivorship-v1"))));

        assertTrue(meters.get("stewardmesh.matching.scoring.duration").timer().count() >= 5);
        assertTrue(meters.get("stewardmesh.matching.decisions")
                .tag("entity", "party").tag("outcome", "review")
                .tag("hard_conflict", "true").counter().count() >= 1);
    }

    private void projectExactGoldenRecord(Scenario exact) {
        var evaluation = evaluations.find(new IdentityResolutionKey(
                exact.source().identity(), SupplierMatchScorer.RULESET.id())).orElseThrow();
        var partyDecision = evaluation.partyDecisions().stream()
                .filter(decision -> decision.candidateId().equals(EXACT_PARTY.value()))
                .filter(decision -> decision.outcome() == MatchOutcome.AUTO_LINK)
                .findFirst().orElseThrow();
        var siteDecision = evaluation.siteDecisions().stream()
                .filter(decision -> decision.candidateId().equals(EXACT_SITE.value()))
                .filter(decision -> decision.outcome() == MatchOutcome.AUTO_LINK)
                .findFirst().orElseThrow();
        var addressId = new SupplierAddressId(
                UUID.fromString("30000000-0000-0000-0000-000000000001"));
        var association = new SourceAssociation(
                new SourceAssociationId(UUID.fromString("40000000-0000-0000-0000-000000000001")),
                exact.source().identity(), EXACT_PARTY, Optional.of(addressId), Optional.of(EXACT_SITE),
                partyDecision, Optional.of(siteDecision), INGESTED_AT.plusSeconds(60), Optional.empty());
        var assertion = new GoldenSourceAssertion(
                exact.source().identity(), exact.source().ingestedAt(), new SourcePriority(100),
                association, goldenValues(exact.source().canonicalValues()));
        var projector = new GoldenRecordProjector();
        var version = new GoldenRecordVersion(1);
        var projectedAt = INGESTED_AT.plusSeconds(120);
        goldenRecords.save(new GoldenRecordProjection(
                projector.projectParty(EXACT_PARTY, version, projectedAt, List.of(assertion)),
                List.of(projector.projectAddress(
                        addressId, EXACT_PARTY, version, projectedAt, List.of(assertion))),
                List.of(projector.projectSite(
                        EXACT_SITE, EXACT_PARTY, addressId, version, projectedAt, List.of(assertion))),
                List.of(association)));
    }

    private Scenario scenario(int sequence, String name, Map<String, String> canonicalValues) {
        var artifactId = new IntakeArtifactId(UUID.nameUUIDFromBytes((name + "-artifact").getBytes()));
        String checksum = "%064x".formatted(sequence);
        artifacts.save(new IntakeArtifact(
                artifactId, checksum, "intake/sha256/" + checksum,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 1, INGESTED_AT));
        var jobId = new ImportJobId(UUID.nameUUIDFromBytes((name + "-job").getBytes()));
        var origin = new SourceSystemRef("SYNTHETIC_" + name);
        imports.save(ImportJob.restore(
                jobId, artifactId, origin, INGESTED_AT, ImportStatus.VALIDATED,
                new ImportCounters(1, 1, 0, 0, 0), null));
        var source = new SourceRecord(
                new SourceRecordIdentity(origin, name.toLowerCase(java.util.Locale.ROOT), 1),
                jobId, INGESTED_AT, SupplierSourceNormalizer.RULESET_ID,
                canonicalValues, canonicalValues);
        sources.writeBatch(jobId, List.of(source), List.of());
        return new Scenario(jobId, source);
    }

    private IdentityResolutionStatus resolutionStatus(Scenario scenario) {
        return statuses.execute(new IdentityResolutionKey(
                scenario.source().identity(), SupplierMatchScorer.RULESET.id()));
    }

    private void seedMatchIndex() {
        seedParty(EXACT_PARTY, "9902000005", "1027700132195", "SYNTHETIC EXACT LLC");
        seedSite(EXACT_SITE, EXACT_PARTY, "9902000005", "990201001", "EXACT", "EXACT STREET");
        seedParty(FUZZY_PARTY, "9903000006", null, "SYNTHETIC ALPHA SERVICES");
        seedParty(CONFLICT_PARTY, "9904000007", "1027700132198", "SYNTHETIC CONFLICT LLC");
        seedParty(NEW_SITE_PARTY, "9906000009", "1027700132197", "SYNTHETIC MULTISITE LLC");
        seedSite(site("20000000-0000-0000-0000-000000000004"), NEW_SITE_PARTY,
                "9906000009", "990601001", "FIRST-SITE", "FIRST STREET");
    }

    private void seedParty(SupplierPartyId partyId, String inn, String ogrn, String legalName) {
        jdbcTemplate.update(
                "INSERT INTO supplier_party_match_index "
                        + "(party_id, canonical_inn, canonical_ogrn, canonical_legal_name) VALUES (?, ?, ?, ?)",
                partyId.value(), inn, ogrn, legalName);
    }

    private void seedSite(
            SupplierSiteId siteId,
            SupplierPartyId partyId,
            String inn,
            String kpp,
            String siteCode,
            String addressLine) {
        jdbcTemplate.update(
                "INSERT INTO supplier_site_match_index "
                        + "(site_id, party_id, canonical_inn, canonical_kpp, canonical_site_code, "
                        + "canonical_country_code, canonical_city, canonical_address_line) "
                        + "VALUES (?, ?, ?, ?, ?, 'RU', 'TEST CITY', ?)",
                siteId.value(), partyId.value(), inn, kpp, siteCode, addressLine);
    }

    private long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private static Map<String, String> values(
            String inn, String ogrn, String legalName, String kpp, String siteCode, String addressLine) {
        var values = new java.util.LinkedHashMap<String, String>();
        values.put("inn", inn);
        if (ogrn != null) {
            values.put("ogrn", ogrn);
        }
        values.put("legal_name", legalName);
        values.put("kpp", kpp);
        values.put("site_code", siteCode);
        values.put("country_code", "RU");
        values.put("city", "TEST CITY");
        values.put("address_line", addressLine);
        return Map.copyOf(values);
    }

    private static Map<GoldenAttributeName, String> goldenValues(Map<String, String> values) {
        var attributes = new EnumMap<GoldenAttributeName, String>(GoldenAttributeName.class);
        for (var name : GoldenAttributeName.values()) {
            if (values.containsKey(name.sourceField())) {
                attributes.put(name, values.get(name.sourceField()));
            }
        }
        return attributes;
    }

    private static RequestPostProcessor identityReadJwt() {
        var jwt = Jwt.withTokenValue("synthetic-phase-2-token")
                .header("alg", "none").subject("synthetic-phase-2-client").build();
        return authentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("SCOPE_identity-resolution.read"))));
    }

    private static SupplierPartyId party(String value) {
        return new SupplierPartyId(UUID.fromString(value));
    }

    private static SupplierSiteId site(String value) {
        return new SupplierSiteId(UUID.fromString(value));
    }

    private record Scenario(ImportJobId jobId, SourceRecord source) {}
}
