package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.application.port.out.IdempotencyRepository;
import io.stewardmesh.masterdata.application.port.out.ImportJobRepository;
import io.stewardmesh.masterdata.application.port.out.IntakeArtifactRepository;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@EntityScan(basePackageClasses = IntakeArtifactEntity.class)
@EnableJpaRepositories(basePackageClasses = SpringDataIntakeArtifactRepository.class)
public class IntakePersistenceConfiguration {

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
}
