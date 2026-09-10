package io.stewardmesh.masterdata.bootstrap;

import io.micrometer.core.instrument.MeterRegistry;
import io.stewardmesh.masterdata.application.identity.CandidateBlockingPolicy;
import io.stewardmesh.masterdata.application.identity.GenerateMatchCandidatesService;
import io.stewardmesh.masterdata.application.identity.ScoreMatchCandidatesService;
import io.stewardmesh.masterdata.application.identity.RouteSupplierImportMatchesService;
import io.stewardmesh.masterdata.application.port.in.GenerateMatchCandidates;
import io.stewardmesh.masterdata.application.port.in.RouteSupplierImportMatches;
import io.stewardmesh.masterdata.application.port.in.ScoreMatchCandidates;
import io.stewardmesh.masterdata.application.port.out.BlockMatchCandidates;
import io.stewardmesh.masterdata.application.port.out.LoadMatchProfiles;
import io.stewardmesh.masterdata.application.port.out.LoadSourceRecord;
import io.stewardmesh.masterdata.application.port.out.ImportJobRepository;
import io.stewardmesh.masterdata.application.port.out.LoadImportMatchWork;
import io.stewardmesh.masterdata.application.port.out.LoadMatchEvaluationSummary;
import io.stewardmesh.masterdata.application.port.out.MatchScoringTelemetry;
import io.stewardmesh.masterdata.application.port.out.StoreStewardshipCase;
import io.stewardmesh.masterdata.application.port.out.StoreMatchEvaluation;
import io.stewardmesh.masterdata.domain.identity.SupplierMatchScorer;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class IdentityResolutionUseCaseConfiguration {

    @Bean
    CandidateBlockingPolicy candidateBlockingPolicy() {
        return CandidateBlockingPolicy.conservativeDefault();
    }

    @Bean
    GenerateMatchCandidates generateMatchCandidates(
            LoadSourceRecord sourceRecords,
            BlockMatchCandidates candidateBlocks,
            CandidateBlockingPolicy policy) {
        return new GenerateMatchCandidatesService(sourceRecords, candidateBlocks, policy);
    }

    @Bean
    SupplierMatchScorer supplierMatchScorer() {
        return new SupplierMatchScorer();
    }

    @Bean
    ScoreMatchCandidates scoreMatchCandidates(
            LoadSourceRecord sourceRecords,
            LoadMatchProfiles profiles,
            StoreMatchEvaluation evaluations,
            MatchScoringTelemetry telemetry,
            SupplierMatchScorer scorer,
            Clock clock) {
        return new ScoreMatchCandidatesService(
                sourceRecords, profiles, evaluations, telemetry, scorer, clock);
    }

    @Bean
    RouteSupplierImportMatches routeSupplierImportMatches(
            ImportJobRepository importJobs,
            LoadImportMatchWork matchWork,
            GenerateMatchCandidates generateCandidates,
            ScoreMatchCandidates scoreCandidates,
            LoadMatchEvaluationSummary evaluationSummaries,
            StoreStewardshipCase stewardshipCases) {
        return new RouteSupplierImportMatchesService(
                importJobs,
                matchWork,
                generateCandidates,
                scoreCandidates,
                evaluationSummaries,
                stewardshipCases);
    }

    @Bean
    MatchScoringTelemetry matchScoringTelemetry(MeterRegistry meterRegistry) {
        return new MicrometerMatchScoringTelemetry(meterRegistry);
    }
}
