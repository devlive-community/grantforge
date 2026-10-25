// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse;

import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The settings of a ClickHouse service, checked. The servers are named by the URL of their HTTP interface, such as
 * {@code https://clickhouse1.example.com:8443}; naming several makes a lookup go to the next server when one cannot be
 * reached. The client signs in with a password, or with a TLS client certificate that ClickHouse maps to the user, and
 * over https verifies the servers against a CA certificate of its own.
 *
 * @param urls the servers' URLs, without a trailing slash
 * @param authentication password or certificate
 * @param user the user name
 * @param password the password, for password
 * @param rootCertificate the PEM file of the CA that verifies the servers, or {@code null} for the JVM's
 * @param clientCertificate the PEM file of the client certificate, for certificate
 * @param clientKey the PEM file of the client certificate's private key, for certificate
 * @param timeout how long a request may take, in milliseconds
 */
record ClickHouseSettings(List<String> urls, String authentication, String user, @Nullable String password, @Nullable String rootCertificate,
        @Nullable String clientCertificate, @Nullable String clientKey, int timeout)
{
    static final String URLS = "clickhouse.urls";
    static final String AUTHENTICATION = "authentication";
    static final String USER = "username";
    static final String PASSWORD = "password";
    static final String ROOT_CERTIFICATE = "ssl.root.certificate";
    static final String CLIENT_CERTIFICATE = "ssl.client.certificate";
    static final String CLIENT_KEY = "ssl.client.key";
    static final String TIMEOUT = "timeout";

    static final String PASSWORD_SIGN_IN = "password";
    static final String CERTIFICATE_SIGN_IN = "certificate";
    static final List<String> AUTHENTICATIONS = List.of(PASSWORD_SIGN_IN, CERTIFICATE_SIGN_IN);
    static final int DEFAULT_TIMEOUT = 30_000;

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
        String urls = text(config, URLS);
        List<String> servers = List.of();
        if (urls == null) {
            problems.add(new ConfigProblem(URLS, ConfigProblem.Reason.REQUIRED,
                    "name a server's HTTP interface, such as http://clickhouse1.example.com:8123, or several separated by commas"));
        }
        else {
            servers = urls(urls);
            if (servers.isEmpty()) {
                problems.add(invalid(URLS, "list the servers as http:// or https:// URLs without a path, separated by commas, such as"
                        + " https://clickhouse1:8443,https://clickhouse2:8443"));
            }
        }
        if (text(config, USER) == null) {
            problems.add(new ConfigProblem(USER, ConfigProblem.Reason.REQUIRED, "name the user that looks up databases, tables and columns"));
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
        if (CERTIFICATE_SIGN_IN.equals(authentication)) {
            for (String field : used) {
                if (text(config, field) == null) {
                    problems.add(new ConfigProblem(field, ConfigProblem.Reason.REQUIRED, "certificate authentication needs " + field));
                }
            }
            if (servers.stream().anyMatch(url -> url.startsWith("http:"))) {
                problems.add(invalid(URLS, "certificate authentication needs https:// servers"));
            }
        }
        if (text(config, ROOT_CERTIFICATE) != null && !servers.isEmpty() && servers.stream().noneMatch(url -> url.startsWith("https:"))) {
            problems.add(invalid(ROOT_CERTIFICATE, "a CA certificate verifies https:// servers; none is named"));
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
     * @param value URLs separated by commas
     * @return them without trailing slashes and with lower-case schemes, or none when one is no http or https URL with a
     *         host and nothing after it
     */
    static List<String> urls(String value)
    {
        List<String> urls = new ArrayList<>();
        for (String entry : value.split(",", -1)) {
            String url = entry.strip();
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            String server = server(url);
            if (server == null) {
                return List.of();
            }
            if (!urls.contains(server)) {
                urls.add(server);
            }
        }
        return urls;
    }

    /** An http or https URL with a host and neither a path, a query, a fragment nor a user, with its scheme in lower case. */
    private static @Nullable String server(String url)
    {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            boolean bare = (uri.getPath() == null || uri.getPath().isEmpty()) && uri.getQuery() == null && uri.getFragment() == null
                    && uri.getUserInfo() == null;
            if (("http".equals(scheme) || "https".equals(scheme)) && uri.getHost() != null && bare) {
                return scheme + url.substring(scheme.length());
            }
            return null;
        }
        catch (URISyntaxException wrong) {
            return null;
        }
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
    static ClickHouseSettings of(ServiceConfig config)
    {
        List<ConfigProblem> problems = problems(config);
        if (!problems.isEmpty()) {
            ConfigProblem first = problems.get(0);
            throw new IllegalArgumentException(first.field() + ": " + first.detail());
        }
        return new ClickHouseSettings(List.copyOf(urls(String.valueOf(text(config, URLS)))), lower(config, AUTHENTICATION, PASSWORD_SIGN_IN),
                String.valueOf(text(config, USER)), config.get(PASSWORD), text(config, ROOT_CERTIFICATE), text(config, CLIENT_CERTIFICATE),
                text(config, CLIENT_KEY), timeout(config));
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
