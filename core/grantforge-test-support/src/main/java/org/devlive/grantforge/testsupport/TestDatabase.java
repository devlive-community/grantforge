// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport;

import org.jspecify.annotations.Nullable;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Locale;

import static java.util.Objects.requireNonNull;

/**
 * A database for the multi-database integration tests, selected by a spec such as {@code h2},
 * {@code postgres:17}, {@code mysql:8.4}, {@code mariadb:11.4}, {@code oracle:23} or {@code sqlserver:2022}.
 * Every engine except H2 runs in a Testcontainers container, so Docker is required for them.
 */
@SuppressWarnings("PMD.TestClassWithoutTestCases") // a test fixture, not a test class
public final class TestDatabase
        implements AutoCloseable
{
    private final String spec;
    private final @Nullable JdbcDatabaseContainer<?> container;

    private TestDatabase(String spec, @Nullable JdbcDatabaseContainer<?> container)
    {
        this.spec = spec;
        this.container = container;
    }

    /**
     * Starts the database named by the system property {@code grantforge.it.database} (default {@code h2}).
     *
     * @return the started database
     */
    public static TestDatabase fromSystemProperty()
    {
        return start(System.getProperty("grantforge.it.database", "h2"));
    }

    /**
     * Starts a database.
     *
     * @param spec {@code h2} or {@code <engine>:<version>}
     * @return the started database
     * @throws IllegalArgumentException for an unknown engine
     */
    public static TestDatabase start(String spec)
    {
        JdbcDatabaseContainer<?> container = container(spec);
        if (container != null) {
            container.start();
        }
        return new TestDatabase(spec, container);
    }

    /**
     * Creates the (not yet started) container for a spec.
     *
     * @param spec {@code h2} or {@code <engine>:<version>}
     * @return the container, or {@code null} for the in-memory H2 database
     */
    static @Nullable JdbcDatabaseContainer<?> container(String spec)
    {
        String[] parts = requireNonNull(spec, "spec").strip().toLowerCase(Locale.ROOT).split(":", 2);
        String engine = parts[0];
        String version = parts.length > 1 ? parts[1] : "";
        return switch (engine) {
            case "h2" -> null;
            case "postgres" -> new PostgreSQLContainer("postgres:" + version + "-alpine");
            case "mysql" -> new MySQLContainer("mysql:" + version);
            // The MariaDB server default character set is latin1; the application requires utf8mb4.
            case "mariadb" -> new MariaDBContainer("mariadb:" + version)
                    .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
            case "oracle" -> new OracleContainer("gvenzl/oracle-free:" + version + "-slim-faststart");
            case "sqlserver" -> new MSSQLServerContainer("mcr.microsoft.com/mssql/server:" + version + "-latest")
                    .acceptLicense();
            default -> throw new IllegalArgumentException("unknown database '" + spec
                    + "'; use h2, postgres:<v>, mysql:<v>, mariadb:<v>, oracle:<v> or sqlserver:<v>");
        };
    }

    /**
     * Returns the JDBC URL.
     *
     * @return the URL
     */
    public String url()
    {
        return container == null ? "jdbc:h2:mem:grantforge_it;DB_CLOSE_DELAY=-1" : container.getJdbcUrl();
    }

    /**
     * Returns the user name.
     *
     * @return the user name
     */
    public String username()
    {
        return container == null ? "sa" : container.getUsername();
    }

    /**
     * Returns the password.
     *
     * @return the password
     */
    public String password()
    {
        return container == null ? "" : container.getPassword();
    }

    @Override
    public void close()
    {
        if (container != null) {
            container.stop();
        }
    }

    @Override
    public String toString()
    {
        return spec;
    }
}
