// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql;

import org.devlive.grantforge.plugin.api.LookupException;
import org.jspecify.annotations.Nullable;
import org.postgresql.Driver;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Read-only connections to the servers of a PostgreSQL service through PostgreSQL's own JDBC driver, one database at a
 * time: PostgreSQL keeps the schemas, tables and columns of each database to itself, so a lookup below the databases
 * signs in to each database it reads, at the first server that can be reached, or the next when one cannot. A server
 * that answers decides, refusals included.
 */
class PostgreSqlClient
{
    /** The driver's TLS factory that checks servers against the JVM's trusted certificates. */
    static final String JAVA_TRUST = "org.postgresql.ssl.DefaultJavaSSLFactory";

    private final PostgreSqlSettings settings;
    private final Connector connector;

    PostgreSqlClient(PostgreSqlSettings settings)
    {
        // The driver itself rather than DriverManager, which only sees drivers of the server's own class loader.
        this(settings, (url, properties) -> new Driver().connect(url, properties));
    }

    PostgreSqlClient(PostgreSqlSettings settings, Connector connector)
    {
        this.settings = settings;
        this.connector = connector;
    }

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
         * @throws SQLException when the server cannot be reached or refuses
         */
        @Nullable Connection connect(String url, Properties properties) throws SQLException;
    }

    /** Work done on a connection. */
    @FunctionalInterface
    interface Work<T>
    {
        /**
         * Does the work.
         *
         * @param connection the connection, signed in and read-only
         * @return the result
         * @throws SQLException when the server refuses or fails
         */
        T run(Connection connection) throws SQLException;
    }

    /**
     * Signs in to a database, runs the work and closes the connection.
     *
     * @param database the database
     * @param work what to do
     * @param <T> the result's type
     * @return what the work returned
     * @throws LookupException when no server can be reached, one refuses the sign-in or fails the work
     */
    <T> T run(String database, Work<T> work)
    {
        LookupException unreachable = null;
        for (String host : settings.hosts()) {
            Connection connection;
            String url = url(host, database);
            try {
                connection = connector.connect(url, properties(settings));
            }
            catch (SQLException failed) {
                LookupException reason = failure(host + "/" + database, failed);
                if (reason.getReason() != LookupException.Reason.UNREACHABLE) {
                    throw reason;
                }
                if (unreachable == null) {
                    unreachable = reason;
                }
                continue;
            }
            if (connection == null) {
                throw new LookupException(LookupException.Reason.FAILED, "the PostgreSQL driver does not accept " + url);
            }
            try (connection) {
                connection.setReadOnly(true);
                return work.run(connection);
            }
            catch (SQLException failed) {
                throw failure(host + "/" + database, failed);
            }
        }
        throw unreachable == null ? new LookupException(LookupException.Reason.UNREACHABLE, "no PostgreSQL server is configured") : unreachable;
    }

    /**
     * Returns the URL of a database on one server. The client goes from one server to the next itself: given several,
     * the driver would skip for a while a server that refused any sign-in of the JVM, a wrong password included.
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
     * Returns what the driver is given besides the URL: the sign-in, TLS and timeouts. Neither a client certificate of
     * the account GrantForge runs as nor its CA certificates in {@code ~/.postgresql} are used unless the service names
     * them.
     *
     * @param settings the service's settings
     * @return the driver's properties
     */
    static Properties properties(PostgreSqlSettings settings)
    {
        Properties properties = new Properties();
        properties.setProperty("user", settings.user());
        if (PostgreSqlSettings.CERTIFICATE_SIGN_IN.equals(settings.authentication())) {
            properties.setProperty("sslcert", String.valueOf(settings.clientCertificate()));
            properties.setProperty("sslkey", String.valueOf(settings.clientKey()));
        }
        else {
            String password = settings.password();
            if (password != null) {
                properties.setProperty("password", password);
            }
            properties.setProperty("sslcert", "");
        }
        properties.setProperty("sslmode", settings.sslMode());
        if (PostgreSqlSettings.verifies(settings.sslMode())) {
            String ca = settings.rootCertificate();
            if (ca == null) {
                properties.setProperty("sslfactory", JAVA_TRUST);
            }
            else {
                properties.setProperty("sslrootcert", ca);
            }
        }
        int seconds = Math.max(1, (settings.timeout() + 999) / 1000);
        properties.setProperty("connectTimeout", Integer.toString(seconds));
        properties.setProperty("loginTimeout", Integer.toString(seconds));
        properties.setProperty("socketTimeout", Integer.toString(seconds));
        properties.setProperty("ApplicationName", "grantforge");
        properties.setProperty("readOnly", "true");
        return properties;
    }

    /**
     * Names why a request failed, from PostgreSQL's SQLSTATE.
     *
     * @param where the servers and database asked
     * @param failed what the driver threw
     * @return the failure
     */
    static LookupException failure(String where, SQLException failed)
    {
        return new LookupException(reason(failed.getSQLState()), where + ": " + message(failed), failed);
    }

    private static LookupException.Reason reason(@Nullable String state)
    {
        if (state == null) {
            return LookupException.Reason.FAILED;
        }
        // 28000 is also an unknown user or a sign-in pg_hba.conf has no entry for; 28P01 a wrong password.
        if (state.startsWith("28")) {
            return LookupException.Reason.AUTHENTICATION_FAILED;
        }
        if ("42501".equals(state)) {
            return LookupException.Reason.ACCESS_DENIED;
        }
        if ("3D000".equals(state)) {
            return LookupException.Reason.NOT_FOUND;
        }
        // Connection failures, a server that is starting or shutting down, and one with no connection left.
        if (state.startsWith("08") || state.startsWith("57P") || "53300".equals(state)) {
            return LookupException.Reason.UNREACHABLE;
        }
        return LookupException.Reason.FAILED;
    }

    /** The first line of a failure, which is the useful part of the driver's message. */
    static String message(Throwable failure)
    {
        String text = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        int line = text.indexOf('\n');
        return (line < 0 ? text : text.substring(0, line)).strip();
    }
}
