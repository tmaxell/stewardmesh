package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.application.port.out.IdempotencyRepository;
import io.stewardmesh.masterdata.application.port.out.ImportJobRepository;
import io.stewardmesh.masterdata.application.port.out.IntakeArtifactRepository;
import io.stewardmesh.masterdata.application.port.out.SourceRecordWriter;
import io.stewardmesh.masterdata.application.port.out.ValidationIssueReader;
import io.stewardmesh.masterdata.persistence.jdbc.JdbcSourceRecordWriter;
import io.stewardmesh.masterdata.persistence.jdbc.JdbcValidationIssueReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EntityScan(basePackageClasses = IntakeArtifactEntity.class)
@EnableJpaRepositories(basePackageClasses = SpringDataIntakeArtifactRepository.class)
public class IntakePersistenceConfiguration {

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
    ValidationIssueReader validationIssueReader(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcValidationIssueReader(jdbcTemplate, objectMapper);
    }
}
