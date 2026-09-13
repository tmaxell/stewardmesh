package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.organization.OrganizationWriteException;
import io.stewardmesh.masterdata.application.port.out.SiteAssignmentRepository;
import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import io.stewardmesh.masterdata.domain.organization.SiteAssignment;
import io.stewardmesh.masterdata.domain.organization.SiteAssignmentId;
import io.stewardmesh.masterdata.domain.organization.SitePurpose;
import java.sql.Date;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcSiteAssignmentRepository implements SiteAssignmentRepository {

    private static final String SELECT = """
            SELECT assignment_id, site_id, client_business_unit_id, purposes,
                   valid_from, valid_to, assignment_version
            FROM site_assignment
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcSiteAssignmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public Optional<SiteAssignment> findById(SiteAssignmentId id) {
        Objects.requireNonNull(id, "id must not be null");
        return jdbcTemplate.query(
                SELECT + " WHERE assignment_id = ?",
                (resultSet, rowNumber) -> map(
                        resultSet.getObject("assignment_id", UUID.class),
                        resultSet.getObject("site_id", UUID.class),
                        resultSet.getObject("client_business_unit_id", UUID.class),
                        resultSet.getString("purposes"),
                        resultSet.getDate("valid_from"),
                        resultSet.getDate("valid_to"),
                        resultSet.getLong("assignment_version")),
                id.value()).stream().findFirst();
    }

    @Override
    public List<SiteAssignment> findForSiteAndClient(
            SupplierSiteId siteId, BusinessUnitId clientBusinessUnitId, int limit) {
        Objects.requireNonNull(siteId, "siteId must not be null");
        Objects.requireNonNull(clientBusinessUnitId, "clientBusinessUnitId must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jdbcTemplate.query(
                SELECT + """
                         WHERE site_id = ? AND client_business_unit_id = ?
                         ORDER BY valid_from, assignment_id
                         LIMIT ?
                        """,
                (resultSet, rowNumber) -> map(
                        resultSet.getObject("assignment_id", UUID.class),
                        resultSet.getObject("site_id", UUID.class),
                        resultSet.getObject("client_business_unit_id", UUID.class),
                        resultSet.getString("purposes"),
                        resultSet.getDate("valid_from"),
                        resultSet.getDate("valid_to"),
                        resultSet.getLong("assignment_version")),
                siteId.value(), clientBusinessUnitId.value(), limit);
    }

    @Override
    public List<SiteAssignment> findConflicts(SiteAssignment candidate, int limit) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jdbcTemplate.query(
                SELECT + """
                         WHERE site_id = ?
                           AND client_business_unit_id = ?
                           AND string_to_array(purposes, ',')
                               && string_to_array(?, ',')
                           AND daterange(valid_from, COALESCE(valid_to, 'infinity'::date), '[]')
                               && daterange(?, COALESCE(?::date, 'infinity'::date), '[]')
                         ORDER BY valid_from, assignment_id
                         LIMIT ?
                        """,
                (resultSet, rowNumber) -> map(
                        resultSet.getObject("assignment_id", UUID.class),
                        resultSet.getObject("site_id", UUID.class),
                        resultSet.getObject("client_business_unit_id", UUID.class),
                        resultSet.getString("purposes"),
                        resultSet.getDate("valid_from"),
                        resultSet.getDate("valid_to"),
                        resultSet.getLong("assignment_version")),
                candidate.siteId().value(),
                candidate.clientBusinessUnitId().value(),
                purposes(candidate),
                Date.valueOf(candidate.validFrom()),
                candidate.validTo().map(Date::valueOf).orElse(null),
                limit);
    }

    @Override
    public SiteAssignment save(SiteAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment must not be null");
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO site_assignment
                        (assignment_id, site_id, client_business_unit_id, purposes,
                         valid_from, valid_to, assignment_version)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    assignment.id().value(),
                    assignment.siteId().value(),
                    assignment.clientBusinessUnitId().value(),
                    purposes(assignment),
                    Date.valueOf(assignment.validFrom()),
                    assignment.validTo().map(Date::valueOf).orElse(null),
                    assignment.version());
            return assignment;
        } catch (DataAccessException exception) {
            throw new OrganizationWriteException("site assignment could not be persisted", exception);
        }
    }

    private static SiteAssignment map(
            UUID id, UUID siteId, UUID clientId, String purposes,
            Date validFrom, Date validTo, long version) {
        var purposeSet = EnumSet.noneOf(SitePurpose.class);
        Arrays.stream(purposes.split(",")).map(SitePurpose::valueOf).forEach(purposeSet::add);
        return new SiteAssignment(
                new SiteAssignmentId(id),
                new SupplierSiteId(siteId),
                new BusinessUnitId(clientId),
                purposeSet,
                validFrom.toLocalDate(),
                Optional.ofNullable(validTo).map(Date::toLocalDate),
                version);
    }

    private static String purposes(SiteAssignment assignment) {
        return assignment.purposes().stream()
                .map(Enum::name)
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }
}
