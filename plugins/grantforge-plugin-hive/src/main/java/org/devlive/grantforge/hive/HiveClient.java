// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive;

import org.apache.hive.jdbc.HiveDriver;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.jspecify.annotations.Nullable;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;

import static java.util.Objects.requireNonNull;

/**
 * One connection to HiveServer2 for one piece of work, through Hive's own JDBC driver: with the lookup user's password
 * (or none, for a server without authentication), or signed in to Kerberos first, the driver then taking the ticket from
 * the calling subject.
 */
final class HiveClient
{
    /** Opens a connection; the driver itself, or a stand-in in tests. */
    @FunctionalInterface
    interface Connector
    {
        /**
         * Connects.
         *
         * @param url the URL
         * @param properties the driver's properties
         * @return the connection
         * @throws SQLException when the server cannot be reached or refuses the sign-in
         */
        @Nullable Connection connect(String url, Properties properties) throws SQLException;
    }

    /** Signs in to Kerberos; the JDK's login module, or a stand-in in tests. */
    @FunctionalInterface
    interface Login
    {
        /**
         * Signs in.
         *
         * @param principal the principal
         * @param keytab its keytab, or {@code null}
         * @param password its password, used without a keytab
         * @param kdcs the KDCs, or {@code null}
         * @return the signed-in subject
         * @throws LoginException when the KDC refuses
         * @throws IOException when the keytab cannot be read
         */
        Subject login(String principal, @Nullable String keytab, @Nullable String password, @Nullable String kdcs)
                throws LoginException, IOException;
    }

    /** Work done on a connection. */
    @FunctionalInterface
    interface Work<T>
    {
        /**
         * Does the work.
         *
         * @param connection the connection, signed in
         * @return the result
         * @throws SQLException when the server fails the work
         */
        T run(Connection connection) throws SQLException;
    }

    private final String url;
    private final boolean kerberos;
    private final String username;
    private final @Nullable String password;
    private final @Nullable String keytab;
    private final @Nullable String kdcs;
    private final Connector connector;
    private final Login login;

    /**
     * Reads a service's settings, connecting with Hive's driver.
     *
     * @param config the service's configuration, checked by {@link HiveProvider#validateConfig}
     * @throws IllegalArgumentException when the URL or a timeout is invalid
     */
    HiveClient(ServiceConfig config)
    {
        this(config, (url, properties) -> new HiveDriver().connect(url, properties), KerberosLogin::login);
    }

    HiveClient(ServiceConfig config, Connector connector, Login login)
    {
        requireNonNull(config, "config");
        String authentication = config.get(HiveProvider.AUTHENTICATION);
        this.kerberos = authentication != null && HiveProvider.KERBEROS.equals(authentication.strip().toLowerCase(Locale.ROOT));
        this.url = HiveUrl.connect(config.require(HiveProvider.URL), kerberos, timeout(config));
        this.username = config.require(HiveProvider.USER).strip();
        this.password = config.get(HiveProvider.PASSWORD);
        this.keytab = blankToNull(config.get(HiveProvider.KEYTAB));
        this.kdcs = blankToNull(config.get(HiveProvider.KDC));
        this.connector = requireNonNull(connector, "connector");
        this.login = requireNonNull(login, "login");
    }

    /**
     * Reads the read timeout of a service.
     *
     * @param config the service's configuration
     * @return the timeout in milliseconds
     * @throws IllegalArgumentException when it is not between 1 ms and 10 minutes
     */
    static int timeout(ServiceConfig config)
    {
        long value = config.getLong(HiveProvider.READ_TIMEOUT, 30_000);
        if (value < 1 || value > 600_000) {
            throw new IllegalArgumentException("the read timeout must be between 1 and 600000 milliseconds");
        }
        return (int) value;
    }

    /**
     * Connects, runs the work and closes the connection.
     *
     * @param work what to do
     * @param <T> the result's type
     * @return what the work returned
     * @throws SQLException when the server cannot be reached, refuses the sign-in or fails the work
     * @throws LoginException when the KDC refuses the lookup principal
     * @throws IOException when the keytab cannot be read or the realm declared
     */
    <T> T run(Work<T> work) throws SQLException, LoginException, IOException
    {
        if (!kerberos) {
            return runConnected(properties(), work);
        }
        Subject subject = login.login(username, keytab, password, kdcs);
        try {
            // The driver opens its Kerberos transport as the subject it is called as.
            return Subjects.callAs(subject, () -> runConnected(new Properties(), work));
        }
        catch (SQLException | LoginException | IOException | RuntimeException failed) {
            throw failed;
        }
        catch (Exception failed) {
            throw new SQLException(failed.getMessage(), failed);
        }
    }

    private <T> T runConnected(Properties properties, Work<T> work) throws SQLException
    {
        try (Connection connection = connector.connect(url, properties)) {
            if (connection == null) {
                throw new SQLException("Hive's driver does not accept " + url);
            }
            return work.run(connection);
        }
    }

    /** The driver's properties for a sign-in with a password, or none. */
    Properties properties()
    {
        Properties properties = new Properties();
        properties.setProperty("user", username);
        if (password != null) {
            properties.setProperty("password", password);
        }
        return properties;
    }

    /** The URL the driver is given. */
    String url()
    {
        return url;
    }

    private static @Nullable String blankToNull(@Nullable String value)
    {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
