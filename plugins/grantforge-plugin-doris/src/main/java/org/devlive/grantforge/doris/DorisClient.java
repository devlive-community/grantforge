// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris;

import org.mariadb.jdbc.Driver;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.util.Properties;

/**
 * One connection to a Doris cluster for one piece of work, through MariaDB Connector/J over the frontends' MySQL
 * protocol: GrantForge may ship that driver, unlike MySQL's own GPL one. The frontends are tried in their order, the
 * next when one cannot be reached; any frontend answers lookups, as each holds the whole catalog.
 */
class DorisClient
{
    private final DorisSettings settings;

    /**
     * Connects with a service's settings.
     *
     * @param settings the settings
     */
    DorisClient(DorisSettings settings)
    {
        this.settings = settings;
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
         * @throws SQLException when the frontend refuses or fails
         */
        T run(Connection connection) throws SQLException;
    }

    /**
     * Connects, runs the work and closes the connection.
     *
     * @param work what to do
     * @param <T> the result's type
     * @return what the work returned
     * @throws SQLException when no frontend can be reached, the sign-in is refused or the work fails
     */
    <T> T run(Work<T> work) throws SQLException
    {
        // The driver itself rather than DriverManager, which only sees drivers of the server's own class loader.
        try (Connection connection = connect()) {
            return work.run(connection);
        }
    }

    /**
     * Opens a connection.
     *
     * @return the connection
     * @throws SQLException when no frontend can be reached or the sign-in is refused
     */
    Connection connect() throws SQLException
    {
        Connection connection = new Driver().connect(url(), properties());
        if (connection == null) {
            throw new SQLNonTransientConnectionException("MariaDB Connector/J does not accept " + url());
        }
        return connection;
    }

    /**
     * Returns the URL the driver is given.
     *
     * @return such as {@code jdbc:mariadb:sequential://fe1:9030,fe2:9030/}
     */
    String url()
    {
        return "jdbc:mariadb:sequential://" + String.join(",", settings.hosts()) + "/";
    }

    /**
     * Returns what the driver is given besides the URL: the sign-in, TLS and timeouts. Doris answers neither local files
     * nor several statements at once, so the driver asks for neither.
     *
     * @return the driver's properties
     */
    Properties properties()
    {
        Properties properties = new Properties();
        properties.setProperty("user", settings.user());
        String password = settings.password();
        if (password != null) {
            properties.setProperty("password", password);
        }
        properties.setProperty("sslMode", settings.sslMode());
        String ca = settings.rootCertificate();
        if (ca != null) {
            properties.setProperty("serverSslCert", ca);
        }
        properties.setProperty("allowLocalInfile", "false");
        properties.setProperty("allowMultiQueries", "false");
        // One try of each frontend when none answers, rather than the driver's 120 tries for its failover modes.
        properties.setProperty("retriesAllDown", Integer.toString(Math.max(1, settings.hosts().size())));
        properties.setProperty("connectTimeout", Integer.toString(settings.timeout()));
        properties.setProperty("socketTimeout", Integer.toString(settings.timeout()));
        return properties;
    }
}
