package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.application.port.out.IdempotencyRepository;
import io.stewardmesh.masterdata.application.port.out.BlockMatchCandidates;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.application.port.out.ImportJobRepository;
import io.stewardmesh.masterdata.application.port.out.IntakeArtifactRepository;
import io.stewardmesh.masterdata.application.port.out.SourceRecordWriter;
import io.stewardmesh.masterdata.application.port.out.LoadSourceRecord;
import io.stewardmesh.masterdata.application.port.out.LoadMatchProfiles;
import io.stewardmesh.masterdata.application.port.out.StoreMatchEvaluation;
import io.stewardmesh.masterdata.application.port.out.LoadImportMatchWork;
import io.stewardmesh.masterdata.application.port.out.LoadMatchEvaluationSummary;
import io.stewardmesh.masterdata.application.port.out.StoreStewardshipCase;
import io.stewardmesh.masterdata.application.port.out.ValidationIssueReader;
import io.stewardmesh.masterdata.persistence.jdbc.JdbcSourceRecordWriter;
import io.stewardmesh.masterdata.persistence.jdbc.JdbcSourceRecordLoader;
import io.stewardmesh.masterdata.persistence.jdbc.JdbcMatchCandidateBlocker;
import io.stewardmesh.masterdata.persistence.jdbc.JdbcMatchEvaluationStore;
import io.stewardmesh.masterdata.persistence.jdbc.JdbcMatchProfileLoader;
import io.stewardmesh.masterdata.persistence.jdbc.JdbcImportMatchWorkLoader;
import io.stewardmesh.masterdata.persistence.jdbc.JdbcMatchEvaluationSummaryLoader;
import io.stewardmesh.masterdata.persistence.jdbc.JdbcStewardshipCaseStore;
import io.stewardmesh.masterdata.persistence.jdbc.JdbcValidationIssueReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EntityScan(basePackageClasses = IntakeArtifactEntity.class)
@EnableJpaRepositories(basePackageClasses = SpringDataIntakeArtifactRepository.class)
public class IntakePersistenceConfiguration {

    @Bean
    JpaGoldenRecordMetadataStore goldenRecordMetadataStore(
            SpringDataGoldenRecordMetadataRepository repository) {
        return new JpaGoldenRecordMetadataStore(repository);
    }

    @Bean
    ApplicationTransaction applicationTransaction(PlatformTransactionManager transactionManager) {
        return new SpringApplicationTransaction(new TransactionTemplate(transactionManager));
    }

    @Bean
    @ConditionalOnMissingBean
    ObjectMapper intakeObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    IntakeArtifactRepository intakeArtifactRepository(
            SpringDataIntakeArtifactRepository repository) {
        return new JpaIntakeArtifactRepository(repository);
    }

    @Bean
    ImportJobRepository importJobRepository(SpringDataImportJobRepository repository) {
        return new JpaImportJobRepository(repository);
    }

    @Bean
    IdempotencyRepository idempotencyRepository(SpringDataIdempotencyRepository repository) {
        return new JpaIdempotencyRepository(repository);
    }

    @Bean
    SourceRecordWriter sourceRecordWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcSourceRecordWriter(jdbcTemplate, objectMapper);
    }

    @Bean
    LoadSourceRecord sourceRecordLoader(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcSourceRecordLoader(jdbcTemplate, objectMapper);
    }

    @Bean
    BlockMatchCandidates matchCandidateBlocker(JdbcTemplate jdbcTemplate) {
        return new JdbcMatchCandidateBlocker(new NamedParameterJdbcTemplate(jdbcTemplate));
    }

    @Bean
    LoadMatchProfiles matchProfileLoader(JdbcTemplate jdbcTemplate) {
        return new JdbcMatchProfileLoader(new NamedParameterJdbcTemplate(jdbcTemplate));
    }

    @Bean
    StoreMatchEvaluation matchEvaluationStore(
            JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcMatchEvaluationStore(jdbcTemplate, objectMapper);
    }

    @Bean
    LoadImportMatchWork importMatchWorkLoader(JdbcTemplate jdbcTemplate) {
        return new JdbcImportMatchWorkLoader(jdbcTemplate);
    }

    @Bean
    LoadMatchEvaluationSummary matchEvaluationSummaryLoader(JdbcTemplate jdbcTemplate) {
        return new JdbcMatchEvaluationSummaryLoader(jdbcTemplate);
    }

    @Bean
    StoreStewardshipCase stewardshipCaseStore(JdbcTemplate jdbcTemplate) {
        return new JdbcStewardshipCaseStore(jdbcTemplate);
    }

    @Bean
    ValidationIssueReader validationIssueReader(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcValidationIssueReader(jdbcTemplate, objectMapper);
    }
}
