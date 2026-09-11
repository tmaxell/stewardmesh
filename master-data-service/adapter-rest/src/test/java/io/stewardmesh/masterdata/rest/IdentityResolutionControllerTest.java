package io.stewardmesh.masterdata.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.stewardmesh.masterdata.application.goldenrecord.GoldenRecordView;
import io.stewardmesh.masterdata.application.identity.IdentityResolutionCandidatePage;
import io.stewardmesh.masterdata.application.identity.IdentityResolutionCandidateQuery;
import io.stewardmesh.masterdata.application.identity.IdentityResolutionKey;
import io.stewardmesh.masterdata.application.identity.IdentityResolutionNotFoundException;
import io.stewardmesh.masterdata.application.identity.IdentityResolutionStatus;
import io.stewardmesh.masterdata.application.identity.MatchExplanationQuery;
import io.stewardmesh.masterdata.application.port.in.GetGoldenRecord;
import io.stewardmesh.masterdata.application.port.in.GetIdentityResolutionStatus;
import io.stewardmesh.masterdata.application.port.in.GetMatchExplanation;
import io.stewardmesh.masterdata.application.port.in.ListIdentityResolutionCandidates;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenEntityType;
import io.stewardmesh.masterdata.domain.goldenrecord.SurvivorshipRulesetId;
import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
import io.stewardmesh.masterdata.domain.identity.MatchFeature;
import io.stewardmesh.masterdata.domain.identity.MatchFeatureCode;
import io.stewardmesh.masterdata.domain.identity.MatchOutcome;
import io.stewardmesh.masterdata.domain.identity.MatchRulesetId;
import io.stewardmesh.masterdata.domain.identity.MatchSignal;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(classes = IdentityResolutionControllerTest.TestApplication.class)
@AutoConfigureMockMvc
class IdentityResolutionControllerTest {

    private static final String BASE =
            "/api/v1/identity-resolution/sources/SYNTHETIC_ERP/source-1/versions/1";
    private static final UUID CANDIDATE =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final IdentityResolutionKey KEY = new IdentityResolutionKey(
            new SourceRecordIdentity(new SourceSystemRef("SYNTHETIC_ERP"), "source-1", 1),
            new MatchRulesetId("supplier-match-v1"));
    private static final Instant EVALUATED_AT = Instant.parse("2026-09-11T08:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReadStubs stubs;

    @BeforeEach
    void reset() {
        stubs.missing = false;
    }

    @Test
    void enforcesDedicatedReadScope() throws Exception {
        mockMvc.perform(get(BASE).param("rulesetId", "supplier-match-v1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get(BASE).param("rulesetId", "supplier-match-v1").with(wrongScope()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void returnsStatusBoundedCandidatesAndCompleteExplanation() throws Exception {
        mockMvc.perform(get(BASE).param("rulesetId", "supplier-match-v1").with(readScope()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partyCandidates").value(1))
                .andExpect(jsonPath("$.source.sourceRecordId").value("source-1"));
        mockMvc.perform(get(BASE + "/candidates")
                        .param("rulesetId", "supplier-match-v1")
                        .param("entityType", "PARTY")
                        .param("size", "1")
                        .with(readScope()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCandidates").value(1))
                .andExpect(jsonPath("$.candidates[0].features").isEmpty());
        mockMvc.perform(get(BASE + "/candidates/{candidateId}/explanation", CANDIDATE)
                        .param("rulesetId", "supplier-match-v1")
                        .param("entityType", "PARTY")
                        .with(readScope()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features[0].code").value("INN_EXACT"))
                .andExpect(jsonPath("$.features[0].signal").value("MATCH"));
    }

    @Test
    void returnsGoldenSnapshotAndStableErrors() throws Exception {
        mockMvc.perform(get("/api/v1/golden-records/PARTY/{id}", CANDIDATE).with(readScope()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityType").value("PARTY"))
                .andExpect(jsonPath("$.rulesetId").value("golden-survivorship-v1"));

        stubs.missing = true;
        mockMvc.perform(get(BASE).param("rulesetId", "supplier-match-v1").with(readScope()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IDENTITY_RESOLUTION_NOT_FOUND"));
        mockMvc.perform(get(BASE + "/candidates")
                        .param("rulesetId", "supplier-match-v1")
                        .param("entityType", "INVALID")
                        .with(readScope()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MATCH_ENTITY_TYPE_INVALID"));
    }

    private static RequestPostProcessor readScope() {
        return authentication(new JwtAuthenticationToken(
                jwt(), List.of(new SimpleGrantedAuthority("SCOPE_identity-resolution.read"))));
    }

    private static RequestPostProcessor wrongScope() {
        return authentication(new JwtAuthenticationToken(
                jwt(), List.of(new SimpleGrantedAuthority("SCOPE_supplier-import.read"))));
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("synthetic-test-token")
                .header("alg", "none").subject("synthetic-test-user").build();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
        IdentityResolutionController.class,
        IntakeApiExceptionHandler.class,
        IntakeRestSecurityConfiguration.class,
        IntakeOpenApiConfiguration.class
    })
    static class TestApplication {

        @Bean
        ReadStubs readStubs() { return new ReadStubs(); }

        @Bean
        GetIdentityResolutionStatus status(ReadStubs stubs) { return stubs::status; }

        @Bean
        ListIdentityResolutionCandidates candidates(ReadStubs stubs) { return stubs::candidates; }

        @Bean
        GetMatchExplanation explanation(ReadStubs stubs) { return stubs::explanation; }

        @Bean
        GetGoldenRecord goldenRecord(ReadStubs stubs) { return query -> stubs.golden(); }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> { throw new IllegalArgumentException("synthetic decoder rejects raw tokens"); };
        }
    }

    static final class ReadStubs {
        private boolean missing;

        IdentityResolutionStatus status(IdentityResolutionKey key) {
            if (missing) { throw new IdentityResolutionNotFoundException(); }
            return new IdentityResolutionStatus(key, EVALUATED_AT, 1, 0, 1, 0, 0, false);
        }

        IdentityResolutionCandidatePage candidates(IdentityResolutionCandidateQuery query) {
            return new IdentityResolutionCandidatePage(
                    query.key(), query.entityType(), query.page(), query.size(), 1, List.of(decision()));
        }

        MatchDecision explanation(MatchExplanationQuery query) { return decision(); }

        GoldenRecordView golden() {
            return new GoldenRecordView(
                    GoldenEntityType.PARTY, CANDIDATE, CANDIDATE, null, 1,
                    new SurvivorshipRulesetId("golden-survivorship-v1"), EVALUATED_AT,
                    List.of(), List.of());
        }

        private static MatchDecision decision() {
            return new MatchDecision(
                    MatchEntityType.PARTY, CANDIDATE, MatchOutcome.AUTO_LINK, 10_000,
                    KEY.rulesetId(), false, List.of(new MatchFeature(
                            MatchFeatureCode.INN_EXACT, MatchSignal.MATCH, 10_000)));
        }
    }
}
