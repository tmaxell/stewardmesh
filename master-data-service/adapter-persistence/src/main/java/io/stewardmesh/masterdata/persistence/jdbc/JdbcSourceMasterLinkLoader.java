package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.goldenrecord.SourceMasterLink;
import io.stewardmesh.masterdata.application.port.out.LoadSourceMasterLinks;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Bounded active-source lookup; does not expose original or canonical supplier values. */
public final class JdbcSourceMasterLinkLoader implements LoadSourceMasterLinks {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSourceMasterLinkLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public List<SourceMasterLink> findActive(SourceRecordIdentity source) {
        Objects.requireNonNull(source, "source must not be null");
        return jdbcTemplate.query(
                """
                SELECT association_id, party_id, address_id, site_id
                FROM source_association
                WHERE origin_system = ? AND source_record_id = ? AND source_version = ?
                  AND unlinked_at IS NULL
                ORDER BY association_id
                LIMIT 2
                """,
                (resultSet, rowNumber) -> new SourceMasterLink(
                        source,
                        resultSet.getObject("association_id", UUID.class),
                        resultSet.getObject("party_id", UUID.class),
                        resultSet.getObject("address_id", UUID.class),
                        resultSet.getObject("site_id", UUID.class)),
                source.originSystem().value(), source.sourceRecordId(), source.sourceVersion());
    }
}
