// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql;

import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.jspecify.annotations.Nullable;
import org.mariadb.jdbc.Driver;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * One read-only connection to a MySQL server for one piece of work, through MariaDB Connector/J: GrantForge may ship it,
 * unlike MySQL's own GPL driver, and it speaks MySQL's protocol including caching_sha2_password and TLS. A service names
 * its server as either driver does, {@code jdbc:mysql://host:3306} or {@code jdbc:mariadb://host:3306}, with the
 * driver's failover forms; the connection signs in with the service's username and password only.
 */
final class MySqlClient
{
    /** {@code jdbc:<driver>:[<failover mode>:]//<hosts>[/<database>][?<options>]}. */
    private static final Pattern URL = Pattern.compile(
            "jdbc:(mysql|mariadb):(?:([a-z][a-z-]*):)?//([^/?#]+)(/[^?#]*)?(?:\\?([^#]*))?");
    private static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,127}");

    /**
     * Driver options a service may not set, compared without case as the driver compares them: the credentials come from
     * their own fields, and the rest would read local files for the server, run statements on connecting, change what the
     * connection may write, or load classes and credentials from elsewhere.
     */
    private static final Set<String> RESERVED = Set.of("user", "password", "allowlocalinfile", "allowmultiqueries", "initsql",
            "createdatabaseifnotexist", "socketfactory", "credentialtype", "pool", "poolname", "registerjmxpool");

    private final String url;
    private final String username;
    private final @Nullable String password;
    private final Map<String, String> options;
    private final int connectTimeout;
    private final int readTimeout;

    /**
     * Reads a service's settings.
     *
     * @param config the service's configuration, checked by {@link MySqlProvider#validateConfig}
     * @throws IllegalArgumentException when the URL, the driver options or a timeout is invalid
     */
    MySqlClient(ServiceConfig config)
    {
        requireNonNull(config, "config");
        this.url = driverUrl(config.require(MySqlProvider.URL));
        this.username = config.require(MySqlProvider.USER).strip();
        this.password = config.get(MySqlProvider.PASSWORD);
        this.options = options(config.get(MySqlProvider.PROPERTIES));
        this.connectTimeout = timeout(config, MySqlProvider.CONNECT_TIMEOUT, 10_000);
        this.readTimeout = timeout(config, MySqlProvider.READ_TIMEOUT, 30_000);
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
     * Connects, runs the work and closes the connection.
     *
     * @param work what to do
     * @param <T> the result's type
     * @return what the work returned
     * @throws SQLException when the server cannot be reached, refuses the sign-in or fails the work
     */
    <T> T run(Work<T> work) throws SQLException
    {
        // The driver itself rather than DriverManager, which only sees drivers of the server's own class loader.
        try (Connection connection = new Driver().connect(url, properties())) {
            if (connection == null) {
                throw new SQLNonTransientConnectionException("MariaDB Connector/J does not accept " + url);
            }
            connection.setReadOnly(true);
            return work.run(connection);
        }
    }

    /** What the driver is given besides the URL; the reserved options are set here, never by the service. */
    Properties properties()
    {
        Properties properties = new Properties();
        properties.putAll(options);
        properties.setProperty("user", username);
        if (password != null) {
            properties.setProperty("password", password);
        }
        properties.setProperty("allowLocalInfile", "false");
        properties.setProperty("allowMultiQueries", "false");
        properties.setProperty("connectTimeout", Integer.toString(connectTimeout));
        properties.setProperty("socketTimeout", Integer.toString(readTimeout));
        return properties;
    }

    /** The URL the driver is given. */
    String url()
    {
        return url;
    }

    /**
     * Checks a service's URL and turns it into one MariaDB Connector/J accepts, which reads {@code jdbc:mysql:} only
     * when the URL itself permits it.
     *
     * @param value the URL as configured
     * @return the same URL under {@code jdbc:mariadb:}
     * @throws IllegalArgumentException when it is no MySQL or MariaDB URL, carries credentials or a reserved option
     */
    static String driverUrl(String value)
    {
        String url = value.strip();
        Matcher matcher = URL.matcher(url);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("use jdbc:mysql://host:3306 or jdbc:mariadb://host:3306, optionally with a failover mode"
                    + " such as jdbc:mysql:replication://primary,replica");
        }
        if (matcher.group(3).indexOf('@') >= 0) {
            throw new IllegalArgumentException("give the username and password in their own fields, not in the URL");
        }
        String query = matcher.group(5);
        if (query != null && !query.isEmpty()) {
            for (String option : query.split("&")) {
                int equals = option.indexOf('=');
                String key = equals < 0 ? option : option.substring(0, equals);
                // An encoded name could hide a reserved option from the check below.
                if (!KEY.matcher(key).matches()) {
                    throw new IllegalArgumentException("the URL option " + key + " is no driver option name");
                }
                reserved(key);
            }
        }
        return "jdbc:mariadb:" + url.substring(("jdbc:" + matcher.group(1) + ":").length());
    }

    /**
     * Reads the driver options of a service, one {@code key=value} per line; blank lines and lines starting with
     * {@code #} are skipped.
     *
     * @param text the options, or {@code null}
     * @return the options in their order
     * @throws IllegalArgumentException when a line is no option, an option repeats or is reserved
     */
    static Map<String, String> options(@Nullable String text)
    {
        Map<String, String> options = new LinkedHashMap<>();
        if (text == null) {
            return options;
        }
        int number = 0;
        for (String raw : text.split("\\R")) {
            number++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int equals = line.indexOf('=');
            String key = equals < 0 ? line : line.substring(0, equals).strip();
            if (equals < 0 || !KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("line " + number + " is not a key=value driver option");
            }
            reserved(key);
            String lower = key.toLowerCase(Locale.ROOT);
            if (options.keySet().stream().anyMatch(known -> known.toLowerCase(Locale.ROOT).equals(lower))) {
                throw new IllegalArgumentException("the driver option " + key + " is given twice");
            }
            options.put(key, line.substring(equals + 1).strip());
        }
        return options;
    }

    private static void reserved(String key)
    {
        if (RESERVED.contains(key.strip().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("the driver option " + key.strip() + " cannot be set for a service");
        }
    }

    /**
     * Reads a timeout of the service.
     *
     * @param config the service's configuration
     * @param field the field
     * @param fallback the timeout when none is set
     * @return the timeout in milliseconds
     * @throws IllegalArgumentException when it is not between 1 ms and 10 minutes
     */
    static int timeout(ServiceConfig config, String field, int fallback)
    {
        long value = config.getLong(field, fallback);
        if (value < 1 || value > 600_000) {
            throw new IllegalArgumentException("the timeout must be between 1 and 600000 milliseconds");
        }
        return (int) value;
    }
}
