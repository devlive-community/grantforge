// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence;

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
 * A database for {@link MultiDatabaseIT}, selected by a spec such as {@code h2}, {@code postgres:17},
 * {@code mysql:8.4}, {@code mariadb:11.4}, {@code oracle:23} or {@code sqlserver:2022}.
 */
final class TestDatabase
        implements AutoCloseable
{
    private final String spec;
    private final @Nullable JdbcDatabaseContainer<?> container;

    private TestDatabase(String spec, @Nullable JdbcDatabaseContainer<?> container)
    {
        this.spec = spec;
        this.container = container;
    }

    static TestDatabase start(String spec)
    {
        String[] parts = requireNonNull(spec, "spec").strip().toLowerCase(Locale.ROOT).split(":", 2);
        String engine = parts[0];
        String version = parts.length > 1 ? parts[1] : "";
        JdbcDatabaseContainer<?> container = switch (engine) {
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
        if (container != null) {
            container.start();
        }
        return new TestDatabase(spec, container);
    }

    String url()
    {
        return container == null ? "jdbc:h2:mem:grantforge_it;DB_CLOSE_DELAY=-1" : container.getJdbcUrl();
    }

    String username()
    {
        return container == null ? "sa" : container.getUsername();
    }

    String password()
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
