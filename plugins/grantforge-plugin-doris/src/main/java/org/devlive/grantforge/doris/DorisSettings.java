// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris;

import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The settings of an Apache Doris service, checked. The cluster is named by its frontends' MySQL protocol addresses,
 * such as {@code fe1.example.com:9030}; naming several makes a lookup go to the next frontend when one cannot be reached.
 * The client signs in with a user name and password, and encrypts the connection as the driver's TLS mode says,
 * verifying the frontends against a CA certificate of its own or the JVM's.
 *
 * @param hosts the frontends as {@code host:port}, in the order they are tried
 * @param user the user name
 * @param password the password, or {@code null} for a user without one
 * @param sslMode disable, trust, verify-ca or verify-full
 * @param rootCertificate the PEM file of the CA that verifies the frontends, or {@code null} for the JVM's
 * @param timeout how long connecting and a statement may take, in milliseconds
 */
record DorisSettings(List<String> hosts, String user, @Nullable String password, String sslMode, @Nullable String rootCertificate, int timeout)
{
    static final String HOSTS = "doris.hosts";
    static final String USER = "username";
    static final String PASSWORD = "password";
    static final String SSL_MODE = "ssl.mode";
    static final String ROOT_CERTIFICATE = "ssl.root.certificate";
    static final String TIMEOUT = "timeout";

    static final String DISABLE = "disable";
    static final String TRUST = "trust";
    static final String VERIFY_CA = "verify-ca";
    static final String VERIFY_FULL = "verify-full";
    /** The driver's TLS modes, as MariaDB Connector/J names them. */
    static final List<String> SSL_MODES = List.of(DISABLE, TRUST, VERIFY_CA, VERIFY_FULL);
    static final int DEFAULT_PORT = 9030;
    static final int DEFAULT_TIMEOUT = 30_000;

    /** A host name, an IPv4 address or a bracketed IPv6 address, with an optional port. */
    private static final Pattern HOST = Pattern.compile("(\\[[0-9A-Fa-f:.]+]|[A-Za-z0-9][A-Za-z0-9.-]*)(?::(\\d{1,5}))?");

    /** Copies the frontends. */
    DorisSettings
    {
        hosts = List.copyOf(hosts);
    }

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
                    "name a frontend's MySQL protocol address, such as fe1.example.com:9030, or several separated by commas"));
        }
        else if (hosts(hosts).isEmpty()) {
            problems.add(invalid(HOSTS, "list the frontends as host:port separated by commas, such as fe1:9030,fe2:9030; the port is 9030 unless"
                    + " given"));
        }
        if (text(config, USER) == null) {
            problems.add(new ConfigProblem(USER, ConfigProblem.Reason.REQUIRED, "name the user that looks up catalogs, databases, tables and"
                    + " columns"));
        }
        String mode = sslMode(config);
        if (!SSL_MODES.contains(mode)) {
            problems.add(invalid(SSL_MODE, "the TLS mode is disable, trust, verify-ca or verify-full"));
        }
        else if (text(config, ROOT_CERTIFICATE) != null && !mode.startsWith("verify-")) {
            problems.add(invalid(ROOT_CERTIFICATE, "a CA certificate verifies the frontends in verify-ca or verify-full mode only"));
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
     * Reads a list of frontends.
     *
     * @param value addresses separated by commas
     * @return them as {@code host:port}, the default port added, without repeats; none when one is no address
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
    static DorisSettings of(ServiceConfig config)
    {
        List<ConfigProblem> problems = problems(config);
        if (!problems.isEmpty()) {
            ConfigProblem first = problems.get(0);
            throw new IllegalArgumentException(first.field() + ": " + first.detail());
        }
        return new DorisSettings(hosts(String.valueOf(text(config, HOSTS))), String.valueOf(text(config, USER)), config.get(PASSWORD),
                sslMode(config), text(config, ROOT_CERTIFICATE), timeout(config));
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

    private static String sslMode(ServiceConfig config)
    {
        String value = text(config, SSL_MODE);
        return value == null ? DISABLE : value.toLowerCase(Locale.ROOT);
    }

    private static @Nullable String text(ServiceConfig config, String field)
    {
        String value = config.get(field);
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Override
    public String toString()
    {
        return "DorisSettings[hosts=" + hosts + ", user=" + user + ", sslMode=" + sslMode + "]";
    }
}
