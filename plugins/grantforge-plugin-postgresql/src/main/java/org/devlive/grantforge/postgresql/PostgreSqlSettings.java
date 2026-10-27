// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql;

import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The settings of a PostgreSQL service, checked. The servers are named as {@code host:port}; naming several makes the
 * driver go to the next one when one cannot be reached. The client signs in with a password, or with a TLS client
 * certificate that PostgreSQL's {@code cert} authentication maps to the user, and checks the server's certificate as the
 * TLS mode says: against a CA certificate of its own, or the JVM's.
 *
 * @param hosts the servers, as {@code host:port}
 * @param database the database the client signs in to, to read the list of databases
 * @param authentication password or certificate
 * @param user the user name
 * @param password the password, for password
 * @param sslMode how TLS is used, as libpq's {@code sslmode} names it
 * @param rootCertificate the PEM file of the CA that verifies the servers, or {@code null} for the JVM's
 * @param clientCertificate the PEM file of the client certificate, for certificate
 * @param clientKey the client certificate's PKCS #8 private key file, for certificate
 * @param timeout how long connecting and a query may take, in milliseconds
 */
record PostgreSqlSettings(List<String> hosts, String database, String authentication, String user, @Nullable String password, String sslMode,
        @Nullable String rootCertificate, @Nullable String clientCertificate, @Nullable String clientKey, int timeout)
{
    static final String HOSTS = "postgresql.hosts";
    static final String DATABASE = "postgresql.database";
    static final String AUTHENTICATION = "authentication";
    static final String USER = "username";
    static final String PASSWORD = "password";
    static final String SSL_MODE = "ssl.mode";
    static final String ROOT_CERTIFICATE = "ssl.root.certificate";
    static final String CLIENT_CERTIFICATE = "ssl.client.certificate";
    static final String CLIENT_KEY = "ssl.client.key";
    static final String TIMEOUT = "timeout";

    static final String PASSWORD_SIGN_IN = "password";
    static final String CERTIFICATE_SIGN_IN = "certificate";
    static final List<String> AUTHENTICATIONS = List.of(PASSWORD_SIGN_IN, CERTIFICATE_SIGN_IN);
    /** libpq's TLS modes, from none to a verified server certificate for the host's name. */
    static final List<String> SSL_MODES = List.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full");
    static final String DEFAULT_SSL_MODE = "prefer";
    static final String DEFAULT_DATABASE = "postgres";
    static final int DEFAULT_PORT = 5432;
    static final int DEFAULT_TIMEOUT = 30_000;

    /** {@code host}, {@code host:port}, {@code [IPv6]} or {@code [IPv6]:port}. */
    private static final Pattern HOST = Pattern.compile("(\\[[0-9A-Fa-f:.]+]|[A-Za-z0-9](?:[A-Za-z0-9.-]{0,252}[A-Za-z0-9])?)(?::(\\d{1,5}))?");

    /** The fields each sign-in uses besides the user name; the others must stay empty. */
    private static final Map<String, List<String>> SIGN_IN_FIELDS = Map.of(PASSWORD_SIGN_IN, List.of(PASSWORD), CERTIFICATE_SIGN_IN,
            List.of(CLIENT_CERTIFICATE, CLIENT_KEY));

    /**
     * Checks a service's settings.
     *
     * @param config the settings
     * @return what is wrong, by field; empty when they are usable
     */
    static List<ConfigProblem> problems(ServiceConfig config)
    {
        List<ConfigProblem> problems = new ArrayList<>();
        String hosts = text(config, HOSTS);
        if (hosts == null) {
            problems.add(new ConfigProblem(HOSTS, ConfigProblem.Reason.REQUIRED,
                    "name a server as host:port, such as postgres1.example.com:5432, or several separated by commas"));
        }
        else if (hosts(hosts).isEmpty()) {
            problems.add(invalid(HOSTS, "list the servers as host or host:port separated by commas, such as pg1:5432,pg2:5432"));
        }
        String database = text(config, DATABASE);
        if (database != null && database.length() > 63) {
            problems.add(invalid(DATABASE, "PostgreSQL names are at most 63 characters"));
        }
        if (text(config, USER) == null) {
            problems.add(new ConfigProblem(USER, ConfigProblem.Reason.REQUIRED, "name the user that looks up databases, schemas, tables and columns"));
        }
        String authentication = lower(config, AUTHENTICATION, PASSWORD_SIGN_IN);
        List<String> used = SIGN_IN_FIELDS.get(authentication);
        if (used == null) {
            problems.add(invalid(AUTHENTICATION, "authentication is password or certificate"));
            used = List.of();
        }
        for (Map.Entry<String, List<String>> signIn : SIGN_IN_FIELDS.entrySet()) {
            for (String field : signIn.getValue()) {
                if (!used.contains(field) && text(config, field) != null) {
                    problems.add(invalid(field, "only " + signIn.getKey() + " authentication uses " + field));
                }
            }
        }
        String mode = lower(config, SSL_MODE, DEFAULT_SSL_MODE);
        if (!SSL_MODES.contains(mode)) {
            problems.add(invalid(SSL_MODE, "the TLS mode is one of " + String.join(", ", SSL_MODES)));
        }
        else {
            if (CERTIFICATE_SIGN_IN.equals(authentication) && SSL_MODES.indexOf(mode) < SSL_MODES.indexOf("require")) {
                problems.add(invalid(SSL_MODE, "certificate authentication needs TLS: require, verify-ca or verify-full"));
            }
            if (text(config, ROOT_CERTIFICATE) != null && !verifies(mode)) {
                problems.add(invalid(ROOT_CERTIFICATE, "only the TLS modes verify-ca and verify-full check the server's certificate"));
            }
            // The driver shows a client certificate only with its own trust of a CA file, not with the JVM's.
            if (CERTIFICATE_SIGN_IN.equals(authentication) && verifies(mode) && text(config, ROOT_CERTIFICATE) == null) {
                problems.add(new ConfigProblem(ROOT_CERTIFICATE, ConfigProblem.Reason.REQUIRED,
                        "certificate authentication with " + mode + " needs the CA certificate that verifies the servers"));
            }
        }
        if (CERTIFICATE_SIGN_IN.equals(authentication)) {
            for (String field : used) {
                if (text(config, field) == null) {
                    problems.add(new ConfigProblem(field, ConfigProblem.Reason.REQUIRED, "certificate authentication needs " + field));
                }
            }
        }
        try {
            timeout(config);
        }
        catch (IllegalArgumentException wrong) {
            problems.add(invalid(TIMEOUT, String.valueOf(wrong.getMessage())));
        }
        return problems;
    }

    /**
     * Reads a list of servers.
     *
     * @param value {@code host[:port]} entries separated by commas
     * @return them as {@code host:port}, each once, or none when one is no host with an optional port from 1 to 65535
     */
    static List<String> hosts(String value)
    {
        List<String> hosts = new ArrayList<>();
        for (String entry : value.split(",", -1)) {
            Matcher matcher = HOST.matcher(entry.strip());
            if (!matcher.matches()) {
                return List.of();
            }
            int port = matcher.group(2) == null ? DEFAULT_PORT : Integer.parseInt(matcher.group(2));
            if (port < 1 || port > 65_535) {
                return List.of();
            }
            String host = matcher.group(1).toLowerCase(Locale.ROOT) + ":" + port;
            if (!hosts.contains(host)) {
                hosts.add(host);
            }
        }
        return hosts;
    }

    /**
     * Returns whether a TLS mode checks the server's certificate.
     *
     * @param mode the mode
     * @return {@code true} for verify-ca and verify-full
     */
    static boolean verifies(String mode)
    {
        return mode.startsWith("verify-");
    }

    private static ConfigProblem invalid(String field, String message)
    {
        return new ConfigProblem(field, ConfigProblem.Reason.INVALID, message);
    }

    /**
     * Reads checked settings.
     *
     * @param config the settings
     * @return them
     * @throws IllegalArgumentException when they are not usable, naming the first problem
     */
    static PostgreSqlSettings of(ServiceConfig config)
    {
        List<ConfigProblem> problems = problems(config);
        if (!problems.isEmpty()) {
            ConfigProblem first = problems.get(0);
            throw new IllegalArgumentException(first.field() + ": " + first.detail());
        }
        String database = text(config, DATABASE);
        return new PostgreSqlSettings(List.copyOf(hosts(String.valueOf(text(config, HOSTS)))), database == null ? DEFAULT_DATABASE : database,
                lower(config, AUTHENTICATION, PASSWORD_SIGN_IN), String.valueOf(text(config, USER)), config.get(PASSWORD),
                lower(config, SSL_MODE, DEFAULT_SSL_MODE), text(config, ROOT_CERTIFICATE), text(config, CLIENT_CERTIFICATE), text(config, CLIENT_KEY),
                timeout(config));
    }

    /**
     * Reads the timeout.
     *
     * @param config the settings
     * @return milliseconds, {@value #DEFAULT_TIMEOUT} unless set
     * @throws IllegalArgumentException when it is no whole number from 1 to 600000
     */
    static int timeout(ServiceConfig config)
    {
        String value = text(config, TIMEOUT);
        if (value == null) {
            return DEFAULT_TIMEOUT;
        }
        int timeout = value.matches("\\d{1,6}") ? Integer.parseInt(value) : 0;
        if (timeout >= 1 && timeout <= 600_000) {
            return timeout;
        }
        throw new IllegalArgumentException("timeout is a whole number of milliseconds from 1 to 600000");
    }

    private static @Nullable String text(ServiceConfig config, String field)
    {
        String value = config.get(field);
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String lower(ServiceConfig config, String field, String fallback)
    {
        String value = text(config, field);
        return (value == null ? fallback : value).toLowerCase(Locale.ROOT);
    }
}
