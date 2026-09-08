package io.stewardmesh.masterdata.persistence;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

abstract class PostgreSqlIntegrationTestSupport {

    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
        Flyway.configure().dataSource(dataSource()).load().migrate();
    }

    static DataSource dataSource() {
        return new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    static JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(dataSource());
    }
}
