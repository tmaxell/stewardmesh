package io.stewardmesh.masterdata.bootstrap;

import io.stewardmesh.masterdata.application.identity.CandidateBlockingPolicy;
import io.stewardmesh.masterdata.application.identity.GenerateMatchCandidatesService;
import io.stewardmesh.masterdata.application.port.in.GenerateMatchCandidates;
import io.stewardmesh.masterdata.application.port.out.BlockMatchCandidates;
import io.stewardmesh.masterdata.application.port.out.LoadSourceRecord;
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
}
