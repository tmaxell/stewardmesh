package io.stewardmesh.masterdata.bootstrap;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final String DOMAIN = "io.stewardmesh.masterdata.domain..";
    private static final String APPLICATION = "io.stewardmesh.masterdata.application..";
    /** Every real adapter package. A name that matches nothing would make the rule vacuous. */
    private static final String[] ADAPTERS = {
        "io.stewardmesh.masterdata.ingestion..",
        "io.stewardmesh.masterdata.mcp..",
        "io.stewardmesh.masterdata.messaging..",
        "io.stewardmesh.masterdata.persistence..",
        "io.stewardmesh.masterdata.rest..",
        "io.stewardmesh.masterdata.bootstrap.."
    };
    private static final String[] FRAMEWORKS = {
        "org.springframework..",
        "jakarta.persistence..",
        "com.fasterxml.jackson..",
        "tools.jackson..",
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
    void everyGuardedPackageNameMatchesRealProductionClasses() {
        for (String adapterPackage : ADAPTERS) {
            String prefix = adapterPackage.substring(0, adapterPackage.length() - "..".length());
            assertTrue(
                    productionClasses.stream()
                            .anyMatch(candidate -> candidate.getPackageName().startsWith(prefix)),
                    "no production class resides in " + adapterPackage
                            + "; the dependency rule would pass vacuously");
        }
    }

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
