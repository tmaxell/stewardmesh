package io.stewardmesh.masterdata.bootstrap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.stewardmesh.masterdata.application.port.out.LoadIntakeArtifact;
import io.stewardmesh.masterdata.application.port.out.ParseSupplierWorkbook;
import io.stewardmesh.masterdata.application.port.out.StoreIntakeArtifact;
import io.stewardmesh.masterdata.application.port.in.StartSupplierImport;
import io.stewardmesh.masterdata.application.port.in.GenerateMatchCandidates;
import io.stewardmesh.masterdata.application.port.in.ScoreMatchCandidates;
import io.stewardmesh.masterdata.application.port.in.RouteSupplierImportMatches;
import io.stewardmesh.masterdata.application.port.in.AssignSupplierSite;
import io.stewardmesh.masterdata.application.port.in.DecideActionPlan;
import io.stewardmesh.masterdata.application.port.in.GetBusinessUnit;
import io.stewardmesh.masterdata.application.port.in.ListSiteAssignments;
import io.stewardmesh.masterdata.application.port.in.GetActionPlan;
import io.stewardmesh.masterdata.application.port.in.ProposeActionPlan;
import io.stewardmesh.masterdata.application.port.in.SimulateActionPlan;
import io.stewardmesh.masterdata.application.port.in.SynchronizeBusinessUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StewardMeshApplicationIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private StoreIntakeArtifact storeIntakeArtifact;

    @Autowired
    private LoadIntakeArtifact loadIntakeArtifact;

    @Autowired
    private ParseSupplierWorkbook parseSupplierWorkbook;

    @Autowired
    private StartSupplierImport startSupplierImport;

    @Autowired
    private GenerateMatchCandidates generateMatchCandidates;

    @Autowired
    private ScoreMatchCandidates scoreMatchCandidates;

    @Autowired
    private RouteSupplierImportMatches routeSupplierImportMatches;

    @Autowired
    private SynchronizeBusinessUnit synchronizeBusinessUnit;

    @Autowired
    private GetBusinessUnit getBusinessUnit;

    @Autowired
    private AssignSupplierSite assignSupplierSite;

    @Autowired
    private ListSiteAssignments listSiteAssignments;

    @Autowired
    private ProposeActionPlan proposeActionPlan;

    @Autowired
    private GetActionPlan getActionPlan;

    @Autowired
    private SimulateActionPlan simulateActionPlan;

    @Autowired
    private DecideActionPlan decideActionPlan;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void loadsCompositionRootWithMigratedPersistenceAndS3Storage() {
        assertSame(storeIntakeArtifact, loadIntakeArtifact);
        assertNotNull(parseSupplierWorkbook);
        assertNotNull(startSupplierImport);
        assertNotNull(generateMatchCandidates);
        assertNotNull(scoreMatchCandidates);
        assertNotNull(routeSupplierImportMatches);
        assertNotNull(synchronizeBusinessUnit);
        assertNotNull(getBusinessUnit);
        assertNotNull(assignSupplierSite);
        assertNotNull(listSiteAssignments);
        assertNotNull(proposeActionPlan);
        assertNotNull(getActionPlan);
        assertNotNull(simulateActionPlan);
        assertNotNull(decideActionPlan);
    }
}
