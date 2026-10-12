// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent;

import org.devlive.grantforge.mysql.agent.sql.Catalog;
import org.devlive.grantforge.mysql.agent.sql.TableName;
import org.jspecify.annotations.Nullable;
import org.mariadb.jdbc.Driver;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Objects.requireNonNull;

/**
 * The server's schema as the proxy's own metadata user sees it in information_schema, through MariaDB Connector/J, and
 * kept for a while: the columns of tables, the stored functions of databases and how the server folds names. The
 * metadata user needs a privilege on every table (REFERENCES is enough and reads no data) and SHOW_ROUTINE, so nothing
 * is hidden from it. Thread-safe; one connection serves all lookups.
 */
public final class JdbcCatalog
        implements Catalog, AutoCloseable
{
    private final String url;
    private final Properties properties;
    private final Duration keep;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<TableName, Kept<List<String>>> columns = new ConcurrentHashMap<>();
    private final Map<String, Kept<Set<String>>> routines = new ConcurrentHashMap<>();
    private @Nullable Connection connection;
    private boolean folds;

    /** A value read at a moment. */
    private record Kept<T>(T value, Instant read)
    {
    }

    /**
     * Connects and reads how the server folds names.
     *
     * @param url the server, as {@code jdbc:mariadb://host:port/}
     * @param properties the driver's options, with the metadata user's credentials
     * @param keep how long what was read is used before it is read again
     * @param clock the clock
     * @throws SQLException when the server cannot be reached, refuses the user, or the user lacks SHOW_ROUTINE
     */
    public JdbcCatalog(String url, Properties properties, Duration keep, Clock clock) throws SQLException
    {
        this.url = requireNonNull(url, "url");
        this.properties = (Properties) properties.clone();
        this.keep = requireNonNull(keep, "keep");
        this.clock = requireNonNull(clock, "clock");
        lock.lock();
        try {
            Connection open = connection();
            try (Statement statement = open.createStatement();
                 ResultSet row = statement.executeQuery("SELECT @@lower_case_table_names, "
                         + "(SELECT COUNT(*) FROM information_schema.USER_PRIVILEGES WHERE PRIVILEGE_TYPE = 'SHOW_ROUTINE'"
                         + " AND GRANTEE = CONCAT('''', SUBSTRING_INDEX(CURRENT_USER(), '@', 1), '''@''', SUBSTRING_INDEX(CURRENT_USER(), '@', -1), ''''))")) {
                if (!row.next()) {
                    throw new SQLException("the MySQL server did not say how it folds names");
                }
                folds = row.getInt(1) != 0;
                if (row.getInt(2) == 0) {
                    throw new SQLException("the proxy's metadata user needs the SHOW_ROUTINE privilege, so no stored function is hidden from it");
                }
            }
        }
        catch (SQLException failed) {
            closeQuietly();
            throw failed;
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public @Nullable List<String> columns(TableName table)
    {
        Kept<List<String>> kept = columns.get(table);
        if (kept == null || !fresh(kept.read())) {
            List<String> read = query("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE " + where("TABLE_SCHEMA") + " AND "
                    + where("TABLE_NAME") + " ORDER BY ORDINAL_POSITION", table.database(), table.table());
            kept = new Kept<>(Collections.unmodifiableList(read), clock.instant());
            columns.put(table, kept);
        }
        // A table without columns is one that does not exist or that the metadata user cannot see.
        return kept.value().isEmpty() ? null : kept.value();
    }

    @Override
    public boolean routine(String database, String name)
    {
        Kept<Set<String>> kept = routines.get(database);
        if (kept == null || !fresh(kept.read())) {
            Set<String> names = new HashSet<>();
            for (String routine : query("SELECT ROUTINE_NAME FROM information_schema.ROUTINES WHERE " + where("ROUTINE_SCHEMA"), database)) {
                names.add(routine.toLowerCase(Locale.ROOT));
            }
            kept = new Kept<>(Collections.unmodifiableSet(names), clock.instant());
            routines.put(database, kept);
        }
        // Routine names compare without case in MySQL.
        return kept.value().contains(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean foldsNames()
    {
        return folds;
    }

    @Override
    public void changed()
    {
        columns.clear();
        routines.clear();
    }

    private String where(String column)
    {
        return folds ? "LOWER(" + column + ") = ?" : column + " = ?";
    }

    private boolean fresh(Instant read)
    {
        return read.plus(keep).isAfter(clock.instant());
    }

    private List<String> query(String sql, String... parameters)
    {
        lock.lock();
        try {
            for (int attempt = 0; ; attempt++) {
                try {
                    return read(sql, parameters);
                }
                catch (SQLException failed) {
                    closeQuietly();
                    // A connection the server dropped is opened again once; a second failure is the server's answer.
                    if (attempt > 0) {
                        throw new IllegalStateException("the MySQL schema cannot be read: " + failed.getMessage(), failed);
                    }
                }
            }
        }
        finally {
            lock.unlock();
        }
    }

    private List<String> read(String sql, String... parameters) throws SQLException
    {
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setString(index + 1, parameters[index]);
            }
            List<String> values = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    values.add(rows.getString(1));
                }
            }
            return values;
        }
    }

    // The connection is the catalog's own and stays open between lookups.
    @SuppressWarnings("PMD.CloseResource")
    private Connection connection() throws SQLException
    {
        Connection open = connection;
        if (open == null || open.isClosed()) {
            open = new Driver().connect(url, properties);
            if (open == null) {
                throw new SQLException("MariaDB Connector/J does not accept " + url);
            }
            open.setReadOnly(true);
            connection = open;
        }
        return open;
    }

    // Clearing the reference is how a broken connection is let go; closing it is this method's work.
    @SuppressWarnings({"PMD.NullAssignment", "PMD.CloseResource"})
    private void closeQuietly()
    {
        Connection open = connection;
        connection = null;
        if (open != null) {
            try {
                open.close();
            }
            catch (SQLException ignored) {
                // It is gone either way.
            }
        }
    }

    @Override
    public void close()
    {
        lock.lock();
        try {
            closeQuietly();
        }
        finally {
            lock.unlock();
        }
    }
}
