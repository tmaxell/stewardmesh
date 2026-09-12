package io.stewardmesh.masterdata.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.stewardmesh.masterdata.application.port.out.LoadIntakeArtifact;
import io.stewardmesh.masterdata.application.port.out.ParseSupplierWorkbook;
import io.stewardmesh.masterdata.application.port.out.StoreIntakeArtifact;
import io.stewardmesh.masterdata.application.port.in.StartSupplierImport;
import io.stewardmesh.masterdata.application.port.in.GenerateMatchCandidates;
import io.stewardmesh.masterdata.application.port.in.ScoreMatchCandidates;
import io.stewardmesh.masterdata.application.port.in.RouteSupplierImportMatches;
import io.stewardmesh.masterdata.application.port.in.AssignSupplierSite;
import io.stewardmesh.masterdata.application.port.in.DecideActionPlan;
import io.stewardmesh.masterdata.application.port.in.ExecuteActionPlan;
import io.stewardmesh.masterdata.application.port.in.GetBusinessUnit;
import io.stewardmesh.masterdata.application.port.in.ListSiteAssignments;
import io.stewardmesh.masterdata.application.port.in.GetActionPlan;
import io.stewardmesh.masterdata.application.port.in.ProposeActionPlan;
import io.stewardmesh.masterdata.application.port.in.SimulateActionPlan;
import io.stewardmesh.masterdata.application.port.in.SynchronizeBusinessUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
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

    @Autowired
    private ExecuteActionPlan executeActionPlan;

    @Autowired
    private ToolCallbackProvider toolCallbacks;

    @Autowired
    private MockMvc mockMvc;

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
        assertNotNull(executeActionPlan);
        assertEquals(6, toolCallbacks.getToolCallbacks().length);
    }

    @Test
    void protectsTheStreamableMcpEndpointWithBearerAuthentication() throws Exception {
        String initialize = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                  "protocolVersion":"2025-06-18","capabilities":{},
                  "clientInfo":{"name":"synthetic-test-client","version":"1.0"}}}
                """;

        mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(initialize))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/mcp")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("SCOPE_mdm.supplier.read")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(initialize))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("stewardmesh-master-data")));
    }
}
