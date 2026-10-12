// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent;

import org.devlive.grantforge.mysql.agent.sql.TableName;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.mariadb.jdbc.Driver;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifies metadata visibility, cache invalidation and reconnects against MySQL itself. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class JdbcCatalogIT
{
    private final GenericContainer<?> mysql = new GenericContainer<>(DockerImageName.parse("mysql:8.4"))
            .withExposedPorts(3306).withEnv("MYSQL_ROOT_PASSWORD", "grantforge-root")
            .withCommand("--lower-case-table-names=1")
            .waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1).withStartupTimeout(Duration.ofMinutes(4)));

    @BeforeAll
    void start() throws SQLException
    {
        mysql.start();
        execute("CREATE DATABASE CaseDb", "CREATE TABLE CaseDb.Orders (id INT, Card VARCHAR(32))",
                "CREATE FUNCTION CaseDb.PriceLabel(price INT) RETURNS INT DETERMINISTIC RETURN price * 2",
                "CREATE USER 'metadata'@'%' IDENTIFIED BY 'meta-secret'",
                "GRANT REFERENCES, SHOW_ROUTINE ON *.* TO 'metadata'@'%'",
                "CREATE USER 'incomplete'@'%' IDENTIFIED BY 'meta-secret'",
                "GRANT REFERENCES ON *.* TO 'incomplete'@'%'");
    }

    @AfterAll
    void stop()
    {
        mysql.stop();
    }

    @Test
    void discoversOrderedColumnsAndCaseInsensitiveRoutinesWithoutReadingData() throws SQLException
    {
        try (JdbcCatalog catalog = catalog(Duration.ZERO, Clock.systemUTC())) {
            assertThat(catalog.foldsNames()).isTrue();
            List<String> columns = requireNonNull(catalog.columns(new TableName("casedb", "orders")), "columns");
            assertThat(columns).containsExactly("id", "Card");
            assertThatThrownBy(() -> columns.add("hidden")).isInstanceOf(UnsupportedOperationException.class);
            assertThat(catalog.columns(new TableName("casedb", "missing"))).isNull();
            assertThat(catalog.routine("casedb", "PRICELABEL")).isTrue();
            assertThat(catalog.routine("casedb", "missing")).isFalse();
        }
        try (Connection metadata = connect(options("metadata")); Statement query = metadata.createStatement()) {
            assertThatThrownBy(() -> query.executeQuery("SELECT * FROM casedb.orders"))
                    .isInstanceOfSatisfying(SQLException.class, failure -> assertThat(failure.getErrorCode()).isEqualTo(1142));
        }
    }

    @Test
    void requiresDirectRoutineVisibilityBeforeStarting()
    {
        assertThatThrownBy(() -> new JdbcCatalog(url(), options("incomplete"), Duration.ZERO, Clock.systemUTC()))
                .isInstanceOf(SQLException.class).hasMessageContaining("SHOW_ROUTINE");
    }

    @Test
    void refreshesAfterExpiryAndExplicitInvalidation() throws SQLException
    {
        execute("CREATE TABLE casedb.cached_items (id INT)");
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.EPOCH);
        TableName table = new TableName("casedb", "cached_items");
        try (JdbcCatalog catalog = catalog(Duration.ofSeconds(30), clock)) {
            assertThat(catalog.columns(table)).containsExactly("id");
            execute("ALTER TABLE casedb.cached_items ADD COLUMN price INT");
            assertThat(catalog.columns(table)).containsExactly("id");
            when(clock.instant()).thenReturn(Instant.EPOCH.plusSeconds(31));
            assertThat(catalog.columns(table)).containsExactly("id", "price");
            execute("ALTER TABLE casedb.cached_items ADD COLUMN note VARCHAR(16)");
            assertThat(catalog.columns(table)).containsExactly("id", "price");
            catalog.changed();
            assertThat(catalog.columns(table)).containsExactly("id", "price", "note");
        }
    }

    @Test
    void reconnectsWithItsOwnCredentialsAfterTheServerDropsTheConnection() throws SQLException
    {
        Properties properties = options("metadata");
        try (JdbcCatalog catalog = new JdbcCatalog(url(), properties, Duration.ZERO, Clock.systemUTC())) {
            properties.setProperty("password", "changed-after-construction");
            List<Long> ids = new ArrayList<>();
            try (Connection root = root(); Statement query = root.createStatement();
                    ResultSet connections = query.executeQuery("SELECT ID FROM information_schema.PROCESSLIST WHERE USER = 'metadata'")) {
                while (connections.next()) {
                    ids.add(connections.getLong(1));
                }
            }
            assertThat(ids).isNotEmpty();
            for (long id : ids) {
                execute("KILL " + id);
            }
            assertThat(catalog.columns(new TableName("casedb", "orders"))).containsExactly("id", "Card");
        }
    }

    private JdbcCatalog catalog(Duration keep, Clock clock) throws SQLException
    {
        return new JdbcCatalog(url(), options("metadata"), keep, clock);
    }

    private String url()
    {
        return "jdbc:mariadb://" + mysql.getHost() + ":" + mysql.getMappedPort(3306) + "/";
    }

    private static Properties options(String user)
    {
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", "meta-secret");
        properties.setProperty("sslMode", "trust");
        properties.setProperty("connectTimeout", "10000");
        properties.setProperty("socketTimeout", "10000");
        return properties;
    }

    private Connection connect(Properties properties) throws SQLException
    {
        return requireNonNull(new Driver().connect(url(), properties), "connection");
    }

    private Connection root() throws SQLException
    {
        Properties properties = options("root");
        properties.setProperty("password", "grantforge-root");
        return connect(properties);
    }

    private void execute(String... statements) throws SQLException
    {
        try (Connection root = root(); Statement command = root.createStatement()) {
            for (String sql : statements) {
                command.execute(sql);
            }
        }
    }
}
