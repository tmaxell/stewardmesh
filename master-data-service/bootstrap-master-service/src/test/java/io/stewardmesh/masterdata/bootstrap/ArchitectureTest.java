package io.stewardmesh.masterdata.bootstrap;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final String DOMAIN = "io.stewardmesh.masterdata.domain..";
    private static final String APPLICATION = "io.stewardmesh.masterdata.application..";
    private static final String[] ADAPTERS = {
        "io.stewardmesh.masterdata.adapter..", "io.stewardmesh.masterdata.bootstrap.."
    };
    private static final String[] FRAMEWORKS = {
        "org.springframework..",
        "jakarta.persistence..",
        "com.fasterxml.jackson..",
        "software.amazon.awssdk..",
        "io.modelcontextprotocol..",
        "org.springframework.ai..",
        "org.apache.poi..",
        "org.mapstruct.."
    };

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.stewardmesh.masterdata");

    @Test
    void domainAndApplicationDoNotDependOnOuterLayers() {
        noClasses()
                .that()
                .resideInAnyPackage(DOMAIN, APPLICATION)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(ADAPTERS)
                .check(productionClasses);
    }

    @Test
    void domainIsFrameworkFree() {
        noClasses()
                .that()
                .resideInAPackage(DOMAIN)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(FRAMEWORKS)
                .check(productionClasses);
    }

    @Test
    void applicationIsFrameworkFree() {
        noClasses()
                .that()
                .resideInAPackage(APPLICATION)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(FRAMEWORKS)
                .check(productionClasses);
    }
}
