// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis;

import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The settings of a Redis service, checked. The deployment is named by one or more of its nodes, such as
 * {@code redis1.example.com:6379}; the rest of a cluster, and the replicas of a primary, are found from them. The client
 * signs in with an ACL user and password, or as the default user, and encrypts the connection as the TLS mode says,
 * verifying the servers against a CA file of its own or the JVM's, with a client certificate where the servers ask for one.
 *
 * @param nodes the nodes as {@code host:port}, in the order they are tried
 * @param user the ACL user, or {@code null} for the default user
 * @param password the password, or {@code null} without one
 * @param tlsMode disable, verify-ca or verify-full
 * @param caFile the PEM file of the CAs that verify the servers, or {@code null} for the JVM's
 * @param certificateFile the PEM file of the client certificate, or {@code null} without one
 * @param keyFile the PEM file of the client certificate's key, or {@code null} when the certificate's file holds it
 * @param keyPassword the password of an encrypted key, or {@code null}
 * @param timeout how long connecting and a command may take, in milliseconds
 */
record RedisSettings(List<String> nodes, @Nullable String user, @Nullable String password, String tlsMode, @Nullable String caFile, @Nullable String certificateFile,
        @Nullable String keyFile, @Nullable String keyPassword, int timeout)
{
    static final String NODES = "redis.nodes";
    static final String USER = "username";
    static final String PASSWORD = "password";
    static final String TLS_MODE = "tls.mode";
    static final String CA_FILE = "tls.ca.file";
    static final String CERTIFICATE_FILE = "tls.certificate.file";
    static final String KEY_FILE = "tls.key.file";
    static final String KEY_PASSWORD = "tls.key.password";
    static final String TIMEOUT = "timeout";

    static final String DISABLE = "disable";
    static final String VERIFY_CA = "verify-ca";
    static final String VERIFY_FULL = "verify-full";
    /** How the client encrypts: not at all, checking the servers' certificates, or their names too. */
    static final List<String> TLS_MODES = List.of(DISABLE, VERIFY_CA, VERIFY_FULL);
    static final int DEFAULT_PORT = 6379;
    static final int DEFAULT_TIMEOUT = 10_000;

    /** A host name, an IPv4 address or a bracketed IPv6 address, with an optional port. */
    private static final Pattern HOST = Pattern.compile("(\\[[0-9A-Fa-f:.]+]|[A-Za-z0-9][A-Za-z0-9.-]*)(?::(\\d{1,5}))?");

    /** Copies the nodes. */
    RedisSettings
    {
        nodes = List.copyOf(nodes);
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
        String nodes = text(config, NODES);
        if (nodes == null) {
            problems.add(new ConfigProblem(NODES, ConfigProblem.Reason.REQUIRED, "name a node, such as redis1.example.com:6379, or several separated by commas"));
        }
        else if (nodes(nodes).isEmpty()) {
            problems.add(invalid(NODES, "list the nodes as host:port separated by commas, such as redis1:6379,redis2:6379; the port is 6379 unless given"));
        }
        String mode = tlsMode(config);
        if (!TLS_MODES.contains(mode)) {
            problems.add(invalid(TLS_MODE, "the TLS mode is disable, verify-ca or verify-full"));
        }
        else if (DISABLE.equals(mode)) {
            for (String field : List.of(CA_FILE, CERTIFICATE_FILE, KEY_FILE)) {
                if (text(config, field) != null) {
                    problems.add(invalid(field, "TLS files are used in verify-ca and verify-full mode only"));
                }
            }
        }
        if (text(config, KEY_FILE) != null && text(config, CERTIFICATE_FILE) == null) {
            problems.add(new ConfigProblem(CERTIFICATE_FILE, ConfigProblem.Reason.REQUIRED, "a key signs in with the client certificate it belongs to"));
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
     * Reads a list of nodes.
     *
     * @param value addresses separated by commas
     * @return them as {@code host:port}, the default port added, without repeats; none when one is no address
     */
    static List<String> nodes(String value)
    {
        List<String> nodes = new ArrayList<>();
        for (String entry : value.split(",", -1)) {
            Matcher matcher = HOST.matcher(entry.strip());
            if (!matcher.matches()) {
                return List.of();
            }
            int port = matcher.group(2) == null ? DEFAULT_PORT : Integer.parseInt(matcher.group(2));
            if (port < 1 || port > 65_535) {
                return List.of();
            }
            String node = matcher.group(1).toLowerCase(Locale.ROOT) + ":" + port;
            if (!nodes.contains(node)) {
                nodes.add(node);
            }
        }
        return nodes;
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
    static RedisSettings of(ServiceConfig config)
    {
        List<ConfigProblem> problems = problems(config);
        if (!problems.isEmpty()) {
            ConfigProblem first = problems.get(0);
            throw new IllegalArgumentException(first.field() + ": " + first.detail());
        }
        String password = config.get(PASSWORD);
        return new RedisSettings(nodes(String.valueOf(text(config, NODES))), text(config, USER), password == null || password.isEmpty() ? null : password,
                tlsMode(config), text(config, CA_FILE), text(config, CERTIFICATE_FILE), text(config, KEY_FILE), config.get(KEY_PASSWORD), timeout(config));
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

    private static String tlsMode(ServiceConfig config)
    {
        String value = text(config, TLS_MODE);
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
        return "RedisSettings[nodes=" + nodes + ", user=" + user + ", tlsMode=" + tlsMode + "]";
    }
}
