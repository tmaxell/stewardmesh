package io.stewardmesh.masterdata.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.stewardmesh.masterdata.application.messaging.CanonicalEventEnvelope;
import io.stewardmesh.masterdata.application.messaging.CanonicalEventEnvelope.DataClassification;
import io.stewardmesh.masterdata.application.port.in.PublishMasterDataEvents;
import io.stewardmesh.masterdata.messaging.sqs.SqsCanonicalEventCodec;
import io.stewardmesh.masterdata.messaging.sqs.SqsReferenceDataConsumer;
import io.stewardmesh.masterdata.mcp.GovernedActionPlanMcpTools;
import io.stewardmesh.masterdata.mcp.GovernedActionPlanMcpTools.BoundPlanRequest;
import io.stewardmesh.masterdata.mcp.GovernedActionPlanMcpTools.DecisionRequest;
import io.stewardmesh.masterdata.mcp.GovernedActionPlanMcpTools.EvidenceInput;
import io.stewardmesh.masterdata.mcp.GovernedActionPlanMcpTools.ExecutionRequest;
import io.stewardmesh.masterdata.mcp.GovernedActionPlanMcpTools.ProposalRequest;
import io.stewardmesh.masterdata.mcp.GovernedActionPlanMcpTools.ProposedStep;
import io.stewardmesh.masterdata.mcp.GovernedActionPlanMcpTools.SourceInput;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Testcontainers
@SpringBootTest(properties = "stewardmesh.messaging.enabled=false")
@Import(Phase3GovernedExecutionEndToEndIT.SqsTestConfiguration.class)
class Phase3GovernedExecutionEndToEndIT {

    private static final String SOURCE_SYSTEM = "synthetic-erp";
    private static final String SOURCE_RECORD_ID = "supplier-phase3";
    private static final String MATCH_RULESET = "supplier-match-v1";
    private static final String SOURCE_QUEUE = "phase3-source-events";
    private static final String MASTER_QUEUE = "phase3-master-events";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final GenericContainer<?> LOCALSTACK = new GenericContainer<>(
                    DockerImageName.parse("localstack/localstack:4.14.0"))
            .withEnv("SERVICES", "sqs")
            .withExposedPorts(4566)
            .waitingFor(Wait.forHttp("/_localstack/health").forStatusCode(200));

    @Autowired
    private GovernedActionPlanMcpTools tools;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SqsClient sqs;

    @Autowired
    private SqsCanonicalEventCodec eventCodec;

    @Autowired
    private SqsReferenceDataConsumer referenceConsumer;

    @Autowired
    private PublishMasterDataEvents publishMasterDataEvents;

    @BeforeAll
    static void createQueues() {
        try (SqsClient client = sqsClient()) {
            client.createQueue(CreateQueueRequest.builder().queueName(SOURCE_QUEUE).build());
            client.createQueue(CreateQueueRequest.builder().queueName(MASTER_QUEUE).build());
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("stewardmesh.messaging.endpoint", () -> endpoint().toString());
        registry.add("stewardmesh.messaging.region", () -> Region.US_EAST_1.id());
        registry.add("stewardmesh.messaging.source-events-queue", () -> SOURCE_QUEUE);
        registry.add("stewardmesh.messaging.master-events-queue", () -> MASTER_QUEUE);
        registry.add(
                "stewardmesh.messaging.allowed-producers",
                () -> "synthetic-distributor,master-data-service");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void executesOneSealedPlanExactlyOnceAcrossTheGovernedMcpBoundary() {
        UUID businessUnitId = synchronizeReferenceDataExactlyOnce();
        UUID importId = seedValidatedSyntheticSource();
        UUID partyId = stablePartyId();

        authenticate("synthetic-agent", "mdm.steward.propose");
        var proposed = tools.createOnboardingProposal(new ProposalRequest(
                importId.toString(),
                List.of(new ProposedStep(
                        1,
                        "CREATE_SUPPLIER_PARTY",
                        partyId.toString(),
                        new SourceInput(SOURCE_SYSTEM, SOURCE_RECORD_ID, 1),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "NO_MATCH_NEW_PARTY",
                        List.of(
                                new EvidenceInput(
                                        "SOURCE_RECORD", SOURCE_SYSTEM + ":" + SOURCE_RECORD_ID, 1),
                                new EvidenceInput("MATCH_EVALUATION", MATCH_RULESET, 1))))));

        authenticate("synthetic-reader", "mdm.supplier.read");
        var simulated = tools.simulateOnboardingPlan(
                new BoundPlanRequest(proposed.planId(), proposed.version(), proposed.hash()));
        assertEquals("EXECUTABLE", simulated.outcome());

        authenticate("synthetic-human", "mdm.steward.approve");
        var approved = tools.approveActionPlan(new DecisionRequest(
                proposed.planId(),
                proposed.version(),
                proposed.hash(),
                "phase3-approval-1",
                "Synthetic evidence reviewed"));
        assertEquals("APPROVED", approved.status());

        authenticate("synthetic-executor", "mdm.plan.execute");
        var request = new ExecutionRequest(
                proposed.planId(),
                proposed.version(),
                proposed.hash(),
                "phase3-execution-1",
                "Execute approved synthetic onboarding");
        var first = tools.executeApprovedPlan(request);
        var replay = tools.executeApprovedPlan(request);

        assertEquals(first, replay);
        assertEquals(1, first.effects().size());
        assertEquals("SupplierCreated", first.effects().getFirst().eventType());
        assertEquals("EXECUTED", text("SELECT status FROM action_plan WHERE plan_id = ?", uuid(proposed.planId())));
        assertEquals(1, count("SELECT COUNT(*) FROM action_plan_execution WHERE plan_id = ?", uuid(proposed.planId())));
        assertEquals(1, count("SELECT COUNT(*) FROM action_plan_execution_effect WHERE execution_id = ?", uuid(first.executionId())));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_event WHERE plan_id = ?", uuid(proposed.planId())));
        assertEquals(1, count("SELECT COUNT(*) FROM outbox_event WHERE correlation_id = ?", uuid(first.correlationId())));
        assertEquals(1, count("SELECT COUNT(*) FROM golden_record_version WHERE entity_type = 'PARTY' AND entity_id = ?", partyId));
        assertEquals(1, count("SELECT COUNT(*) FROM source_association WHERE party_id = ?", partyId));

        assertEquals(1, publishMasterDataEvents.execute(10));
        String masteredBody = receiveMasteredEvent();
        CanonicalEventEnvelope mastered = eventCodec.decode(masteredBody);
        assertEquals("STEWARDMESH", mastered.originSystem());
        assertEquals("master-data-service", mastered.producer());
        assertEquals("SupplierCreated", mastered.eventType());
        assertEquals("PUBLISHED", text("SELECT publication_status FROM outbox_event WHERE event_id = ?", mastered.eventId()));

        send(SOURCE_QUEUE, masteredBody);
        assertEquals(1, referenceConsumer.receiveOnce(1));
        assertEquals(
                "LOOP_SUPPRESSED",
                text("SELECT processing_status FROM inbox_event WHERE event_id = ?", mastered.eventId()));
        assertEquals(1, count("SELECT COUNT(*) FROM golden_record_version WHERE entity_type = 'PARTY' AND entity_id = ?", partyId));
        assertEquals(1, count("SELECT COUNT(*) FROM business_unit_version WHERE business_unit_id = ?", businessUnitId));
    }

    private UUID synchronizeReferenceDataExactlyOnce() {
        UUID businessUnitId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-13T07:55:00Z");
        var envelope = new CanonicalEventEnvelope(
                UUID.randomUUID(),
                "BusinessUnitReferenceChanged",
                1,
                "BUSINESS_UNIT",
                businessUnitId.toString(),
                1,
                "SYNTHETIC_NSI",
                "synthetic-distributor",
                "sqs",
                occurredAt,
                occurredAt.plusSeconds(1),
                UUID.randomUUID(),
                Optional.empty(),
                "phase3-reference-trace",
                DataClassification.INTERNAL,
                Map.of(
                        "code", "SYNTH-PHASE3",
                        "displayName", "Synthetic Phase 3 Procurement",
                        "roles", "CLIENT,PROCUREMENT",
                        "validFrom", "2026-01-01"));
        String body = eventCodec.encode(envelope);

        send(SOURCE_QUEUE, body);
        assertEquals(1, referenceConsumer.receiveOnce(1));
        send(SOURCE_QUEUE, body);
        assertEquals(1, referenceConsumer.receiveOnce(1));

        assertEquals("ACCEPTED", text("SELECT processing_status FROM inbox_event WHERE event_id = ?", envelope.eventId()));
        assertEquals(1, count("SELECT COUNT(*) FROM inbox_event WHERE event_id = ?", envelope.eventId()));
        assertEquals(1, count("SELECT COUNT(*) FROM business_unit WHERE business_unit_id = ?", businessUnitId));
        assertEquals(1, count("SELECT COUNT(*) FROM business_unit_version WHERE business_unit_id = ?", businessUnitId));
        return businessUnitId;
    }

    private String receiveMasteredEvent() {
        var messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl(MASTER_QUEUE))
                        .maxNumberOfMessages(1)
                        .waitTimeSeconds(10)
                        .build())
                .messages();
        assertFalse(messages.isEmpty(), "master event queue must receive the committed outbox event");
        var message = messages.getFirst();
        sqs.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl(MASTER_QUEUE))
                .receiptHandle(message.receiptHandle())
                .build());
        return message.body();
    }

    private void send(String queue, String body) {
        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl(queue))
                .messageBody(body)
                .build());
    }

    private String queueUrl(String queue) {
        return sqs.getQueueUrl(builder -> builder.queueName(queue)).queueUrl();
    }

    private UUID seedValidatedSyntheticSource() {
        UUID artifactId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-13T08:00:00Z");
        jdbcTemplate.update(
                "INSERT INTO intake_artifact (id, sha256, storage_key, content_type, size_bytes, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                artifactId,
                "d".repeat(64),
                "synthetic/phase3.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                128,
                Timestamp.from(now));
        jdbcTemplate.update(
                "INSERT INTO import_job (id, artifact_id, source_system, status, total_rows, accepted_rows, created_at) VALUES (?, ?, ?, 'VALIDATED', 1, 1, ?)",
                importId,
                artifactId,
                SOURCE_SYSTEM,
                Timestamp.from(now));
        jdbcTemplate.update(
                """
                INSERT INTO source_record
                    (origin_system, source_record_id, source_version, import_job_id, ingested_at,
                     original_values, canonical_values, canonical_inn, normalization_ruleset)
                VALUES (?, ?, 1, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, 'supplier-source-v1')
                """,
                SOURCE_SYSTEM,
                SOURCE_RECORD_ID,
                importId,
                Timestamp.from(now),
                "{\"legal_name\":\"Synthetic Phase 3 Supplier\",\"inn\":\"9902000005\"}",
                "{\"legal_name\":\"Synthetic Phase 3 Supplier\",\"inn\":\"9902000005\"}",
                "9902000005");
        jdbcTemplate.update(
                "INSERT INTO match_evaluation (origin_system, source_record_id, source_version, ruleset_id, evaluated_at) VALUES (?, ?, 1, ?, ?)",
                SOURCE_SYSTEM,
                SOURCE_RECORD_ID,
                MATCH_RULESET,
                Timestamp.from(now));
        return importId;
    }

    private static UUID stablePartyId() {
        String identity = SOURCE_SYSTEM + '\u001f' + SOURCE_RECORD_ID + '\u001f' + 1 + '\u001f' + "party";
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    private static void authenticate(String subject, String scope) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                subject,
                "n/a",
                List.of(new SimpleGrantedAuthority("SCOPE_" + scope))));
    }

    private int count(String sql, Object argument) {
        return jdbcTemplate.queryForObject(sql, Integer.class, argument);
    }

    private String text(String sql, Object argument) {
        return jdbcTemplate.queryForObject(sql, String.class, argument);
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private static URI endpoint() {
        return URI.create("http://" + LOCALSTACK.getHost() + ":" + LOCALSTACK.getMappedPort(4566));
    }

    private static SqsClient sqsClient() {
        return SqsClient.builder()
                .endpointOverride(endpoint())
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .httpClientBuilder(ApacheHttpClient.builder())
                .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqsTestConfiguration {
        @Bean(destroyMethod = "close")
        @Primary
        SqsClient phase3SqsClient() {
            return sqsClient();
        }
    }
}
