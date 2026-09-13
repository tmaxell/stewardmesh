package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.actionplan.ActionPlanConflictException;
import io.stewardmesh.masterdata.application.actionplan.ActionPlanWriteException;
import io.stewardmesh.masterdata.application.port.out.ActionPlanRepository;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanFingerprint;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanHash;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStep;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanVersion;
import io.stewardmesh.masterdata.domain.actionplan.ActionReasonCode;
import io.stewardmesh.masterdata.domain.actionplan.ActionType;
import io.stewardmesh.masterdata.domain.actionplan.AssignSupplierSiteStep;
import io.stewardmesh.masterdata.domain.actionplan.CreateSupplierPartyStep;
import io.stewardmesh.masterdata.domain.actionplan.CreateSupplierSiteStep;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceReference;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceType;
import io.stewardmesh.masterdata.domain.actionplan.GovernedActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.LinkSourceRecordStep;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import io.stewardmesh.masterdata.domain.organization.SiteAssignmentId;
import io.stewardmesh.masterdata.domain.organization.SitePurpose;
import io.stewardmesh.masterdata.persistence.jpa.ActionPlanHeader;
import io.stewardmesh.masterdata.persistence.jpa.JpaActionPlanHeaderStore;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores sealed plans as an immutable step projection next to the JPA-owned governed status.
 * Reads rebuild the domain plan through its canonical constructor, so a stored plan whose content
 * no longer matches its recorded hash cannot be returned.
 */
public class JdbcActionPlanRepository implements ActionPlanRepository {

    private static final String UNDECIDED_CONTENT_INDEX = "action_plan_undecided_content_idx";
    private static final String INSERT_STEP = """
            INSERT INTO action_plan_step
                (plan_id, step_sequence, action_type, reason_code, party_id,
                 expected_party_version, site_id, expected_site_version, address_id,
                 procurement_business_unit_id, assignment_id, client_business_unit_id,
                 purposes, valid_from, valid_to, origin_system, source_record_id, source_version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_EVIDENCE = """
            INSERT INTO action_plan_step_evidence
                (plan_id, step_sequence, evidence_type, evidence_reference, evidence_version)
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String SELECT_STEPS = """
            SELECT step_sequence, action_type, reason_code, party_id, expected_party_version,
                   site_id, expected_site_version, address_id, procurement_business_unit_id,
                   assignment_id, client_business_unit_id, purposes, valid_from, valid_to,
                   origin_system, source_record_id, source_version
            FROM action_plan_step
            WHERE plan_id = ?
            ORDER BY step_sequence
            """;
    private static final String SELECT_EVIDENCE = """
            SELECT step_sequence, evidence_type, evidence_reference, evidence_version
            FROM action_plan_step_evidence
            WHERE plan_id = ?
            ORDER BY step_sequence, evidence_type, evidence_reference, evidence_version
            """;

    private final JdbcTemplate jdbcTemplate;
    private final JpaActionPlanHeaderStore headerStore;

    public JdbcActionPlanRepository(
            JdbcTemplate jdbcTemplate, JpaActionPlanHeaderStore headerStore) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.headerStore = Objects.requireNonNull(headerStore, "headerStore must not be null");
    }

    @Override
    public Optional<GovernedActionPlan> findById(ActionPlanId id) {
        Objects.requireNonNull(id, "id must not be null");
        return headerStore.findById(id.value()).map(this::rebuild);
    }

    @Override
    public Optional<GovernedActionPlan> findProposedByFingerprint(
            ActionPlanFingerprint fingerprint) {
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");
        return headerStore.findUndecided(fingerprint.value()).map(this::rebuild);
    }

    @Override
    @Transactional
    public GovernedActionPlan save(GovernedActionPlan governed) {
        Objects.requireNonNull(governed, "governed must not be null");
        var stored = headerStore.findById(governed.id().value());
        if (stored.isPresent()) {
            return advance(stored.orElseThrow(), governed);
        }
        return insert(governed);
    }

    private GovernedActionPlan advance(ActionPlanHeader stored, GovernedActionPlan governed) {
        if (!stored.planHash().equals(governed.hash().value())) {
            throw new ActionPlanConflictException("sealed action plan content cannot be changed");
        }
        try {
            headerStore.advance(governed.id().value(), governed.status());
            return governed;
        } catch (DataAccessException exception) {
            throw new ActionPlanWriteException(
                    "action plan status could not be advanced", exception);
        }
    }

    private GovernedActionPlan insert(GovernedActionPlan governed) {
        requireStorableCreationTime(governed);
        try {
            headerStore.insert(governed);
            governed.plan().steps().forEach(step -> insertStep(governed.id().value(), step));
            return governed;
        } catch (DataIntegrityViolationException exception) {
            if (violatesUndecidedContent(exception)) {
                throw new ActionPlanConflictException(
                        "another proposal already sealed this content", exception);
            }
            throw new ActionPlanWriteException("action plan could not be persisted", exception);
        } catch (DataAccessException exception) {
            throw new ActionPlanWriteException("action plan could not be persisted", exception);
        }
    }

    /** Only the partial unique index on undecided content means a concurrent identical proposal. */
    private static boolean violatesUndecidedContent(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null
                    && cause.getMessage().contains(UNDECIDED_CONTENT_INDEX)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The creation time is part of the plan hash. PostgreSQL keeps microseconds, so a finer
     * instant would come back changed and its hash would no longer validate.
     */
    private static void requireStorableCreationTime(GovernedActionPlan governed) {
        var createdAt = governed.plan().createdAt();
        if (!createdAt.equals(createdAt.truncatedTo(ChronoUnit.MICROS))) {
            throw new ActionPlanConflictException(
                    "action plan creation time must not be finer than microseconds");
        }
    }

    private void insertStep(UUID planId, ActionPlanStep step) {
        jdbcTemplate.update(statement -> {
            var prepared = statement.prepareStatement(INSERT_STEP);
            prepared.setObject(1, planId);
            prepared.setInt(2, step.sequence());
            prepared.setString(3, step.type().name());
            prepared.setString(4, step.reasonCode().value());
            bindShape(prepared, step);
            return prepared;
        });
        step.evidence().forEach(evidence -> jdbcTemplate.update(
                INSERT_EVIDENCE,
                planId,
                step.sequence(),
                evidence.type().name(),
                evidence.reference(),
                evidence.version()));
    }

    private static void bindShape(PreparedStatement prepared, ActionPlanStep step)
            throws SQLException {
        for (int column = 5; column <= 18; column++) {
            prepared.setNull(column, columnType(column));
        }
        switch (step) {
            case CreateSupplierPartyStep create -> {
                prepared.setObject(5, create.partyId().value());
                bindSource(prepared, create.sourceRecord());
            }
            case LinkSourceRecordStep link -> {
                prepared.setObject(5, link.partyId().value());
                prepared.setLong(6, link.expectedPartyVersion());
                bindSource(prepared, link.sourceRecord());
            }
            case CreateSupplierSiteStep create -> {
                prepared.setObject(5, create.partyId().value());
                prepared.setLong(6, create.expectedPartyVersion());
                prepared.setObject(7, create.siteId().value());
                prepared.setObject(9, create.addressId().value());
                prepared.setObject(10, create.procurementBusinessUnitId().value());
            }
            case AssignSupplierSiteStep assign -> {
                prepared.setObject(7, assign.siteId().value());
                prepared.setLong(8, assign.expectedSiteVersion());
                prepared.setObject(11, assign.assignmentId().value());
                prepared.setObject(12, assign.clientBusinessUnitId().value());
                prepared.setString(13, purposes(assign.purposes()));
                prepared.setDate(14, Date.valueOf(assign.validFrom()));
                if (assign.validTo().isPresent()) {
                    prepared.setDate(15, Date.valueOf(assign.validTo().orElseThrow()));
                }
            }
        }
    }

    private static void bindSource(
            PreparedStatement prepared, SourceRecordIdentity source) throws SQLException {
        prepared.setString(16, source.originSystem().value());
        prepared.setString(17, source.sourceRecordId());
        prepared.setLong(18, source.sourceVersion());
    }

    private static int columnType(int column) {
        return switch (column) {
            case 5, 7, 9, 10, 11, 12 -> Types.OTHER;
            case 6, 8, 18 -> Types.BIGINT;
            case 13, 16, 17 -> Types.VARCHAR;
            case 14, 15 -> Types.DATE;
            default -> Types.NULL;
        };
    }

    private static String purposes(Set<SitePurpose> purposes) {
        return purposes.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    private GovernedActionPlan rebuild(ActionPlanHeader header) {
        Map<Integer, List<EvidenceReference>> evidence = loadEvidence(header.planId());
        List<ActionPlanStep> steps = jdbcTemplate.query(
                SELECT_STEPS,
                (resultSet, rowNumber) -> toStep(resultSet, evidence),
                header.planId());
        try {
            ActionPlan plan = new ActionPlan(
                    new ActionPlanId(header.planId()),
                    new ActionPlanVersion(header.planVersion()),
                    new ImportJobId(header.importJobId()),
                    header.proposedAt(),
                    header.proposedBySubject(),
                    steps,
                    new ActionPlanHash(header.planHash()));
            return new GovernedActionPlan(plan, header.status());
        } catch (IllegalArgumentException exception) {
            throw new ActionPlanConflictException(
                    "stored action plan no longer matches its sealed hash", exception);
        }
    }

    private Map<Integer, List<EvidenceReference>> loadEvidence(UUID planId) {
        var rows = jdbcTemplate.query(
                SELECT_EVIDENCE,
                (resultSet, rowNumber) -> Map.entry(
                        resultSet.getInt("step_sequence"),
                        new EvidenceReference(
                                EvidenceType.valueOf(resultSet.getString("evidence_type")),
                                resultSet.getString("evidence_reference"),
                                resultSet.getLong("evidence_version"))),
                planId);
        return rows.stream().collect(Collectors.groupingBy(
                Map.Entry::getKey,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private static ActionPlanStep toStep(
            ResultSet resultSet, Map<Integer, List<EvidenceReference>> evidenceBySequence)
            throws SQLException {
        int sequence = resultSet.getInt("step_sequence");
        var reason = new ActionReasonCode(resultSet.getString("reason_code"));
        List<EvidenceReference> evidence =
                new ArrayList<>(evidenceBySequence.getOrDefault(sequence, List.of()));
        return switch (ActionType.valueOf(resultSet.getString("action_type"))) {
            case CREATE_SUPPLIER_PARTY -> new CreateSupplierPartyStep(
                    sequence, partyId(resultSet), source(resultSet), reason, evidence);
            case LINK_SOURCE_RECORD -> new LinkSourceRecordStep(
                    sequence,
                    source(resultSet),
                    partyId(resultSet),
                    resultSet.getLong("expected_party_version"),
                    reason,
                    evidence);
            case CREATE_SUPPLIER_SITE -> new CreateSupplierSiteStep(
                    sequence,
                    new SupplierSiteId(resultSet.getObject("site_id", UUID.class)),
                    partyId(resultSet),
                    resultSet.getLong("expected_party_version"),
                    new SupplierAddressId(resultSet.getObject("address_id", UUID.class)),
                    new BusinessUnitId(
                            resultSet.getObject("procurement_business_unit_id", UUID.class)),
                    reason,
                    evidence);
            case ASSIGN_SUPPLIER_SITE -> new AssignSupplierSiteStep(
                    sequence,
                    new SiteAssignmentId(resultSet.getObject("assignment_id", UUID.class)),
                    new SupplierSiteId(resultSet.getObject("site_id", UUID.class)),
                    resultSet.getLong("expected_site_version"),
                    new BusinessUnitId(
                            resultSet.getObject("client_business_unit_id", UUID.class)),
                    purposes(resultSet.getString("purposes")),
                    resultSet.getDate("valid_from").toLocalDate(),
                    Optional.ofNullable(resultSet.getDate("valid_to"))
                            .map(Date::toLocalDate),
                    reason,
                    evidence);
        };
    }

    private static SupplierPartyId partyId(ResultSet resultSet) throws SQLException {
        return new SupplierPartyId(resultSet.getObject("party_id", UUID.class));
    }

    private static SourceRecordIdentity source(ResultSet resultSet) throws SQLException {
        return new SourceRecordIdentity(
                new SourceSystemRef(resultSet.getString("origin_system")),
                resultSet.getString("source_record_id"),
                resultSet.getLong("source_version"));
    }

    private static Set<SitePurpose> purposes(String stored) {
        var purposes = EnumSet.noneOf(SitePurpose.class);
        Arrays.stream(stored.split(",")).map(SitePurpose::valueOf).forEach(purposes::add);
        return purposes;
    }
}
