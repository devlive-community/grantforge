// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.jspecify.annotations.Nullable;
import org.mariadb.jdbc.Driver;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Doris cluster through MariaDB Connector/J over the frontends' MySQL protocol: one connection, to the first frontend
 * that can be reached, or the next when one is not. Any frontend takes the agent's statements, as followers send
 * changes on to the master themselves. A connection that breaks is opened again once. Not thread-safe: the sync and
 * the audit log's reader each have their own.
 */
final class JdbcDoris
        implements Doris
{
    private static final Logger LOG = Logger.getLogger(JdbcDoris.class.getName());

    private final AgentConfig.Connection settings;
    private final Connector connector;
    private @Nullable Connection connection;

    /** Opens a connection, as the driver does. */
    @FunctionalInterface
    interface Connector
    {
        /**
         * Connects.
         *
         * @param url the frontends
         * @param properties the sign-in, TLS and timeouts
         * @return the connection, or {@code null} when the driver does not take the URL
         * @throws SQLException when no frontend can be reached or the sign-in is refused
         */
        @Nullable Connection connect(String url, Properties properties) throws SQLException;
    }

    JdbcDoris(AgentConfig.Connection settings)
    {
        this(settings, (url, properties) -> new Driver().connect(url, properties));
    }

    JdbcDoris(AgentConfig.Connection settings, Connector connector)
    {
        this.settings = settings;
        this.connector = connector;
    }

    /**
     * Returns the URL of the frontends, tried in their order.
     *
     * @param settings the connection settings
     * @return such as {@code jdbc:mariadb:sequential://fe1:9030,fe2:9030/}
     */
    static String url(AgentConfig.Connection settings)
    {
        return "jdbc:mariadb:sequential://" + String.join(",", settings.hosts()) + "/";
    }

    /**
     * Returns what the driver is given besides the URL: the sign-in, TLS and timeouts.
     *
     * @param settings the connection settings
     * @return the driver's properties
     */
    static Properties properties(AgentConfig.Connection settings)
    {
        Properties properties = new Properties();
        properties.setProperty("user", settings.user());
        String password = settings.password();
        if (password != null) {
            properties.setProperty("password", password);
        }
        properties.setProperty("sslMode", settings.sslMode());
        String ca = settings.ca();
        if (ca != null) {
            properties.setProperty("serverSslCert", ca);
        }
        properties.setProperty("allowLocalInfile", "false");
        properties.setProperty("allowMultiQueries", "false");
        // One try of each frontend when none answers, rather than the driver's 120 tries for its failover modes.
        properties.setProperty("retriesAllDown", Integer.toString(Math.max(1, settings.hosts().size())));
        String millis = Long.toString(Math.max(1, settings.timeout().toMillis()));
        properties.setProperty("connectTimeout", millis);
        properties.setProperty("socketTimeout", millis);
        return properties;
    }

    @Override
    public List<Row> query(String sql, Object... parameters)
    {
        List<Row> rows = retried(open -> rows(open, sql, parameters));
        return rows == null ? List.of() : rows;
    }

    @Override
    public void execute(String sql)
    {
        retried(open -> {
            try (Statement statement = open.createStatement()) {
                statement.execute(sql);
            }
            return null;
        });
    }

    /** What is done on the connection. */
    @FunctionalInterface
    private interface Use<T>
    {
        @Nullable T apply(Connection connection) throws SQLException;
    }

    /** Runs on the open connection, and once more on a new one when the open one broke. */
    // failure() keeps what the driver threw as the cause.
    @SuppressWarnings("PMD.PreserveStackTrace")
    private <T> @Nullable T retried(Use<T> use)
    {
        boolean known = connection != null;
        try {
            return use.apply(connection());
        }
        catch (SQLException failed) {
            DorisFailure failure = failure(failed);
            if (!failure.unreachable()) {
                throw failure;
            }
            forget();
            if (!known) {
                throw failure;
            }
            try {
                return use.apply(connection());
            }
            catch (SQLException again) {
                DorisFailure retried = failure(again);
                if (retried.unreachable()) {
                    forget();
                }
                throw retried;
            }
        }
    }

    private Connection connection() throws SQLException
    {
        Connection open = connection;
        if (open != null) {
            return open;
        }
        Connection opened = connector.connect(url(settings), properties(settings));
        if (opened == null) {
            throw new SQLNonTransientConnectionException("MariaDB Connector/J does not accept " + url(settings));
        }
        connection = opened;
        return opened;
    }

    // It is the connection this closes; none is open until the next statement opens one.
    @SuppressWarnings({"PMD.CloseResource", "PMD.NullAssignment"})
    private void forget()
    {
        Connection open = connection;
        connection = null;
        if (open != null) {
            try {
                open.close();
            }
            catch (SQLException ignored) {
                LOG.log(Level.FINE, "A broken connection could not be closed", ignored);
            }
        }
    }

    @Override
    public void close()
    {
        forget();
    }

    /**
     * Turns what the driver threw into a failure.
     *
     * @param failed the driver's exception
     * @return the failure, unreachable for a connection that could not be made or broke
     */
    static DorisFailure failure(SQLException failed)
    {
        String state = failed.getSQLState();
        boolean unreachable = failed instanceof SQLNonTransientConnectionException || failed instanceof SQLTransientConnectionException
                || state != null && state.startsWith("08");
        return new DorisFailure(message(failed), unreachable, failed);
    }

    /**
     * Returns the useful part of a failure's message: its first line, without the driver's connection number and Doris's
     * error code wrapper.
     *
     * @param failure the failure
     * @return such as {@code Access denied; you need (at least one of) the (DROP) privilege(s) for this operation}
     */
    static String message(Throwable failure)
    {
        String text = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        int line = text.indexOf('\n');
        text = (line < 0 ? text : text.substring(0, line)).strip();
        return text.replaceFirst("^\\(conn=\\d+\\)\\s*", "").replaceFirst("^errCode = \\d+, detailMessage = ", "");
    }

    // A row and a value per row and column read.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    static List<Row> rows(Connection connection, String sql, Object... parameters) throws SQLException
    {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            List<Row> rows = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                ResultSetMetaData meta = result.getMetaData();
                while (result.next()) {
                    Map<String, @Nullable Object> values = new LinkedHashMap<>();
                    for (int column = 1; column <= meta.getColumnCount(); column++) {
                        values.put(meta.getColumnLabel(column), result.getObject(column));
                    }
                    rows.add(new Row(values));
                }
            }
            return rows;
        }
    }
}
