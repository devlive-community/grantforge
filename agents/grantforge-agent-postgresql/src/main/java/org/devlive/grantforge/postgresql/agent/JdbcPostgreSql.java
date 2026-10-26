// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.jspecify.annotations.Nullable;
import org.postgresql.Driver;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PostgreSQL through its own JDBC driver: one connection per database, to the first server that can be reached and is
 * the primary, as only the primary takes changes, or the next when one is not. A connection that breaks is opened again
 * once, at the first server that answers. A text that holds more than one statement is refused before it is sent, as
 * the driver would send each of them. Not thread-safe: the sync and the server log's reader each have their own.
 */
final class JdbcPostgreSql
        implements PostgreSql
{
    private static final Logger LOG = Logger.getLogger(JdbcPostgreSql.class.getName());

    /** The driver's TLS factory that checks servers against the JVM's trusted certificates. */
    static final String JAVA_TRUST = "org.postgresql.ssl.DefaultJavaSSLFactory";

    private final AgentConfig.Connection settings;
    private final Connector connector;
    private final Map<String, Connection> connections = new HashMap<>();

    /** Opens a connection, as the driver does. */
    @FunctionalInterface
    interface Connector
    {
        /**
         * Connects.
         *
         * @param url the server and database
         * @param properties the sign-in, TLS and timeouts
         * @return the connection, or {@code null} when the driver does not take the URL
         * @throws SQLException when the server cannot be reached, refuses, or is no primary
         */
        @Nullable Connection connect(String url, Properties properties) throws SQLException;
    }

    JdbcPostgreSql(AgentConfig.Connection settings)
    {
        this(settings, (url, properties) -> new Driver().connect(url, properties));
    }

    JdbcPostgreSql(AgentConfig.Connection settings, Connector connector)
    {
        this.settings = settings;
        this.connector = connector;
    }

    /**
     * Returns the URL of a database on one server. The agent goes from one server to the next itself: given several,
     * the driver would skip for a while a server that refused any sign-in of the JVM.
     *
     * @param host the server, as {@code host:port}
     * @param database the database
     * @return such as {@code jdbc:postgresql://pg1:5432/sales}
     */
    static String url(String host, String database)
    {
        return "jdbc:postgresql://" + host + "/" + URLEncoder.encode(database, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Returns what the driver is given besides the URL: the sign-in, TLS, timeouts, and that the server must be the
     * primary. Neither a client certificate of the account the agent runs as nor its CA certificates in
     * {@code ~/.postgresql} are used unless the settings name them.
     *
     * @param settings the agent's connection settings
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
        String certificate = settings.certificate();
        String key = settings.key();
        properties.setProperty("sslcert", certificate == null ? "" : certificate);
        if (key != null) {
            properties.setProperty("sslkey", key);
        }
        properties.setProperty("sslmode", settings.sslMode());
        if (settings.sslMode().startsWith("verify-")) {
            String ca = settings.ca();
            if (ca == null) {
                properties.setProperty("sslfactory", JAVA_TRUST);
            }
            else {
                properties.setProperty("sslrootcert", ca);
            }
        }
        long seconds = Math.max(1, (settings.timeout().toMillis() + 999) / 1000);
        properties.setProperty("connectTimeout", Long.toString(seconds));
        properties.setProperty("loginTimeout", Long.toString(seconds));
        properties.setProperty("socketTimeout", Long.toString(seconds));
        properties.setProperty("targetServerType", "primary");
        properties.setProperty("ApplicationName", "grantforge-agent");
        return properties;
    }

    @Override
    public List<Row> query(String database, String sql, Object... parameters)
    {
        List<Row> rows = retried(database, connection -> rows(connection, sql, parameters));
        return rows == null ? List.of() : rows;
    }

    @Override
    public void execute(String database, String sql)
    {
        retried(database, connection -> {
            run(connection, sql);
            return null;
        });
    }

    // The connection stays open for the next statements; close() closes it.
    @SuppressWarnings("PMD.CloseResource")
    @Override
    public void transaction(String database, Work work)
    {
        Connection connection = connection(database);
        try {
            connection.setAutoCommit(false);
            try {
                work.run(new JdbcSession(database, connection));
                connection.commit();
            }
            catch (RuntimeException failed) {
                rollback(connection);
                throw failed;
            }
            catch (SQLException failed) {
                rollback(connection);
                throw failure(database, failed);
            }
            finally {
                connection.setAutoCommit(true);
            }
        }
        catch (SQLException broken) {
            forget(database);
            throw failure(database, broken);
        }
        catch (PostgreSqlFailure failed) {
            if (failed.unreachable()) {
                forget(database);
            }
            throw failed;
        }
    }

    private static void rollback(Connection connection)
    {
        try {
            connection.rollback();
        }
        catch (SQLException unreachable) {
            LOG.log(Level.FINE, "The rollback failed; the server rolls back the transaction of a closed connection", unreachable);
        }
    }

    /** What a session does on a connection. */
    @FunctionalInterface
    private interface Use<T>
    {
        @Nullable T apply(Connection connection) throws SQLException;
    }

    /** Runs on the database's connection, and once more on a new one when the connection broke. */
    // failure() keeps what the driver threw as the cause.
    @SuppressWarnings("PMD.PreserveStackTrace")
    private <T> @Nullable T retried(String database, Use<T> use)
    {
        boolean known = connections.containsKey(database);
        try {
            return use.apply(connection(database));
        }
        catch (SQLException failed) {
            PostgreSqlFailure failure = failure(database, failed);
            if (!failure.unreachable()) {
                throw failure;
            }
            forget(database);
            if (!known) {
                throw failure;
            }
            try {
                return use.apply(connection(database));
            }
            catch (SQLException again) {
                PostgreSqlFailure retried = failure(database, again);
                if (retried.unreachable()) {
                    forget(database);
                }
                throw retried;
            }
        }
    }

    /** The database's open connection, or a new one to the first server that is reachable and the primary. */
    // A failure per server tried.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private Connection connection(String database)
    {
        Connection open = connections.get(database);
        if (open != null) {
            return open;
        }
        PostgreSqlFailure first = null;
        for (String host : settings.hosts()) {
            try {
                Connection connection = connector.connect(url(host, database), properties(settings));
                if (connection == null) {
                    throw new PostgreSqlFailure("the PostgreSQL driver does not accept " + url(host, database), null, null);
                }
                connections.put(database, connection);
                return connection;
            }
            catch (SQLException failed) {
                PostgreSqlFailure failure = new PostgreSqlFailure(host + "/" + database + ": " + message(failed), failed.getSQLState(), failed);
                // A refused sign-in or a missing database is the same at every server.
                if (!failure.unreachable()) {
                    throw failure;
                }
                if (first == null) {
                    first = failure;
                }
            }
        }
        throw first == null ? new PostgreSqlFailure("no PostgreSQL server is configured", null, null) : first;
    }

    // It is the connection this closes.
    @SuppressWarnings("PMD.CloseResource")
    private void forget(String database)
    {
        Connection connection = connections.remove(database);
        if (connection != null) {
            try {
                connection.close();
            }
            catch (SQLException ignored) {
                LOG.log(Level.FINE, "A broken connection could not be closed", ignored);
            }
        }
    }

    private PostgreSqlFailure failure(String database, SQLException failed)
    {
        return new PostgreSqlFailure(database + ": " + message(failed), failed.getSQLState(), failed);
    }

    /** The first line of a failure, which is the useful part of the driver's message. */
    static String message(Throwable failure)
    {
        String text = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        int line = text.indexOf('\n');
        return (line < 0 ? text : text.substring(0, line)).strip();
    }

    private static void run(Connection connection, String sql) throws SQLException
    {
        Sql.single(sql, false);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }

    // A row and a value per row and column read.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    static List<Row> rows(Connection connection, String sql, Object... parameters) throws SQLException
    {
        Sql.single(sql, true);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                bind(connection, statement, index + 1, parameters[index]);
            }
            List<Row> rows = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                ResultSetMetaData columns = result.getMetaData();
                while (result.next()) {
                    Map<String, @Nullable Object> values = new LinkedHashMap<>();
                    for (int column = 1; column <= columns.getColumnCount(); column++) {
                        values.put(columns.getColumnLabel(column), value(result.getObject(column)));
                    }
                    rows.add(new Row(values));
                }
            }
            return rows;
        }
    }

    private static void bind(Connection connection, PreparedStatement statement, int index, Object parameter) throws SQLException
    {
        if (parameter instanceof List<?> list) {
            statement.setArray(index, connection.createArrayOf("text", list.stream().map(String::valueOf).toArray()));
        }
        else if (parameter instanceof Long || parameter instanceof Integer) {
            statement.setLong(index, ((Number) parameter).longValue());
        }
        else if (parameter instanceof Boolean flag) {
            statement.setBoolean(index, flag);
        }
        else {
            statement.setString(index, parameter.toString());
        }
    }

    /** A value as rows hold it: arrays as lists of text, numbers, flags, text and bytes as they are, anything else as text. */
    private static @Nullable Object value(@Nullable Object value) throws SQLException
    {
        if (value instanceof Array array) {
            Object items = array.getArray();
            return items instanceof Object[] objects ? Arrays.stream(objects).map(String::valueOf).toList() : List.of();
        }
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof String || value instanceof byte[]) {
            return value;
        }
        return value.toString();
    }

    @Override
    public void close()
    {
        for (String database : List.copyOf(connections.keySet())) {
            forget(database);
        }
    }

    /** A transaction on one connection; a statement that may fail runs under a savepoint. */
    private final class JdbcSession
            implements Session
    {
        private final String database;
        private final Connection connection;

        JdbcSession(String database, Connection connection)
        {
            this.database = database;
            this.connection = connection;
        }

        @Override
        public void execute(String sql)
        {
            try {
                run(connection, sql);
            }
            catch (SQLException failed) {
                throw failure(database, failed);
            }
        }

        // failure() keeps what the driver threw as the cause.
        @SuppressWarnings("PMD.PreserveStackTrace")
        @Override
        public @Nullable PostgreSqlFailure attempt(String sql)
        {
            Savepoint savepoint;
            try {
                savepoint = connection.setSavepoint();
            }
            catch (SQLException broken) {
                throw failure(database, broken);
            }
            try {
                run(connection, sql);
                connection.releaseSavepoint(savepoint);
                return null;
            }
            catch (SQLException failed) {
                PostgreSqlFailure failure = failure(database, failed);
                if (failure.unreachable()) {
                    throw failure;
                }
                try {
                    connection.rollback(savepoint);
                }
                catch (SQLException broken) {
                    throw failure(database, broken);
                }
                return failure;
            }
        }

        @Override
        public List<Row> query(String sql, Object... parameters)
        {
            try {
                return rows(connection, sql, parameters);
            }
            catch (SQLException failed) {
                throw failure(database, failed);
            }
        }
    }
}
