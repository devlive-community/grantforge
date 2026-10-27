// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch;

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
 * The settings of an Elasticsearch service, checked. The nodes are named as URLs, as Kibana names them; naming several
 * makes the client go to the next one when one cannot be reached. The client signs in with a password, with an API
 * key, or with a TLS client certificate that a PKI realm maps to a user, and checks the nodes' certificates as the
 * verification mode says, with the names Kibana gives its own settings: against CA certificates of its own, or the
 * JVM's.
 *
 * @param hosts the nodes, as {@code scheme://host:port}
 * @param authentication password, api_key or certificate
 * @param user the user name, for password
 * @param password the password, for password
 * @param apiKey the encoded API key, for api_key
 * @param verificationMode full, certificate or none
 * @param certificateAuthorities the PEM file of the CAs that verify the nodes, or {@code null} for the JVM's
 * @param certificate the PEM file of the client certificate, for certificate
 * @param key the client certificate's private key file, for certificate
 * @param keyPassphrase the passphrase of an encrypted key, or {@code null}
 * @param timeout how long connecting and a request may take, in milliseconds
 */
record ElasticsearchSettings(List<String> hosts, String authentication, @Nullable String user, @Nullable String password, @Nullable String apiKey,
        String verificationMode, @Nullable String certificateAuthorities, @Nullable String certificate, @Nullable String key,
        @Nullable String keyPassphrase, int timeout)
{
    static final String HOSTS = "elasticsearch.hosts";
    static final String AUTHENTICATION = "authentication";
    static final String USER = "username";
    static final String PASSWORD = "password";
    static final String API_KEY = "apiKey";
    static final String VERIFICATION_MODE = "ssl.verificationMode";
    static final String CERTIFICATE_AUTHORITIES = "ssl.certificateAuthorities";
    static final String CERTIFICATE = "ssl.certificate";
    static final String KEY = "ssl.key";
    static final String KEY_PASSPHRASE = "ssl.keyPassphrase";
    static final String TIMEOUT = "timeout";

    static final String PASSWORD_SIGN_IN = "password";
    static final String API_KEY_SIGN_IN = "api_key";
    static final String CERTIFICATE_SIGN_IN = "certificate";
    static final List<String> AUTHENTICATIONS = List.of(PASSWORD_SIGN_IN, API_KEY_SIGN_IN, CERTIFICATE_SIGN_IN);
    /** How a node's certificate is checked: its chain and name, its chain only, or not at all. */
    static final List<String> VERIFICATION_MODES = List.of("full", "certificate", "none");
    static final String FULL = "full";
    static final String NONE = "none";
    static final int DEFAULT_PORT = 9200;
    static final int DEFAULT_TIMEOUT = 30_000;

    /** {@code [scheme://]host[:port]} with a host name, an IPv4 address or an {@code [IPv6]} address, and nothing after. */
    private static final Pattern HOST = Pattern.compile(
            "(?:([A-Za-z][A-Za-z0-9+.-]*)://)?(\\[[0-9A-Fa-f:.]+]|[A-Za-z0-9](?:[A-Za-z0-9.-]{0,252}[A-Za-z0-9])?)(?::(\\d{1,5}))?/?");
    /** An encoded API key, as Elasticsearch returns it: Base64 of {@code id:key}. */
    private static final Pattern ENCODED_KEY = Pattern.compile("[A-Za-z0-9+/]+={0,2}");

    /** The fields each sign-in uses; the others must stay empty. */
    private static final Map<String, List<String>> SIGN_IN_FIELDS = Map.of(PASSWORD_SIGN_IN, List.of(USER, PASSWORD), API_KEY_SIGN_IN,
            List.of(API_KEY), CERTIFICATE_SIGN_IN, List.of(CERTIFICATE, KEY, KEY_PASSPHRASE));

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
        List<String> nodes = hosts == null ? List.of() : hosts(hosts);
        if (hosts == null) {
            problems.add(new ConfigProblem(HOSTS, ConfigProblem.Reason.REQUIRED,
                    "name a node as a URL, such as https://es1.example.com:9200, or several separated by commas"));
        }
        else if (nodes.isEmpty()) {
            problems.add(invalid(HOSTS, "list the nodes as http:// or https:// URLs with a host and an optional port, separated by commas"));
        }
        else if (nodes.stream().map(node -> node.startsWith("https:")).distinct().count() > 1) {
            problems.add(invalid(HOSTS, "the nodes of one cluster are all http or all https"));
        }
        boolean secure = nodes.isEmpty() || nodes.get(0).startsWith("https:");
        String authentication = lower(config, AUTHENTICATION, PASSWORD_SIGN_IN);
        List<String> used = SIGN_IN_FIELDS.get(authentication);
        if (used == null) {
            problems.add(invalid(AUTHENTICATION, "authentication is password, api_key or certificate"));
            used = List.of();
        }
        for (Map.Entry<String, List<String>> signIn : SIGN_IN_FIELDS.entrySet()) {
            for (String field : signIn.getValue()) {
                if (!used.contains(field) && text(config, field) != null) {
                    problems.add(invalid(field, "only " + signIn.getKey() + " authentication uses " + field));
                }
            }
        }
        if (PASSWORD_SIGN_IN.equals(authentication)) {
            if (text(config, USER) == null) {
                problems.add(new ConfigProblem(USER, ConfigProblem.Reason.REQUIRED, "name the user that looks up indices, fields and the cluster"));
            }
            if (config.get(PASSWORD) == null || String.valueOf(config.get(PASSWORD)).isEmpty()) {
                problems.add(new ConfigProblem(PASSWORD, ConfigProblem.Reason.REQUIRED, "password authentication needs the user's password"));
            }
        }
        if (API_KEY_SIGN_IN.equals(authentication)) {
            String apiKey = text(config, API_KEY);
            if (apiKey == null) {
                problems.add(new ConfigProblem(API_KEY, ConfigProblem.Reason.REQUIRED, "api_key authentication needs an encoded API key"));
            }
            else if (!ENCODED_KEY.matcher(apiKey).matches()) {
                problems.add(invalid(API_KEY, "the API key is the encoded value Elasticsearch returns when it creates one, not its id or name"));
            }
        }
        if (CERTIFICATE_SIGN_IN.equals(authentication)) {
            for (String field : List.of(CERTIFICATE, KEY)) {
                if (text(config, field) == null) {
                    problems.add(new ConfigProblem(field, ConfigProblem.Reason.REQUIRED, "certificate authentication needs " + field));
                }
            }
            if (!secure) {
                problems.add(invalid(HOSTS, "certificate authentication needs https:// nodes"));
            }
        }
        String mode = lower(config, VERIFICATION_MODE, FULL);
        if (!VERIFICATION_MODES.contains(mode)) {
            problems.add(invalid(VERIFICATION_MODE, "the verification mode is full, certificate or none"));
        }
        else if (NONE.equals(mode) && text(config, CERTIFICATE_AUTHORITIES) != null) {
            problems.add(invalid(CERTIFICATE_AUTHORITIES, "the verification mode none checks no certificate"));
        }
        if (!secure) {
            for (String field : List.of(VERIFICATION_MODE, CERTIFICATE_AUTHORITIES)) {
                if (text(config, field) != null) {
                    problems.add(invalid(field, field + " is for https:// nodes"));
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
     * Reads a list of nodes.
     *
     * @param value {@code [scheme://]host[:port]} entries separated by commas; without a scheme a node is https
     * @return them as {@code scheme://host:port}, each once, or none when one is no http or https node with an optional
     *         port from 1 to 65535
     */
    static List<String> hosts(String value)
    {
        List<String> hosts = new ArrayList<>();
        for (String entry : value.split(",", -1)) {
            Matcher matcher = HOST.matcher(entry.strip());
            if (!matcher.matches()) {
                return List.of();
            }
            String scheme = matcher.group(1) == null ? "https" : matcher.group(1).toLowerCase(Locale.ROOT);
            int port = matcher.group(3) == null ? DEFAULT_PORT : Integer.parseInt(matcher.group(3));
            if (!"http".equals(scheme) && !"https".equals(scheme) || port < 1 || port > 65_535) {
                return List.of();
            }
            String host = scheme + "://" + matcher.group(2).toLowerCase(Locale.ROOT) + ":" + port;
            if (!hosts.contains(host)) {
                hosts.add(host);
            }
        }
        return hosts;
    }

    /**
     * Returns whether the nodes are reached over TLS.
     *
     * @return {@code true} for https nodes
     */
    boolean secure()
    {
        return hosts.get(0).startsWith("https:");
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
    static ElasticsearchSettings of(ServiceConfig config)
    {
        List<ConfigProblem> problems = problems(config);
        if (!problems.isEmpty()) {
            ConfigProblem first = problems.get(0);
            throw new IllegalArgumentException(first.field() + ": " + first.detail());
        }
        return new ElasticsearchSettings(List.copyOf(hosts(String.valueOf(text(config, HOSTS)))), lower(config, AUTHENTICATION, PASSWORD_SIGN_IN),
                text(config, USER), config.get(PASSWORD), text(config, API_KEY), lower(config, VERIFICATION_MODE, FULL),
                text(config, CERTIFICATE_AUTHORITIES), text(config, CERTIFICATE), text(config, KEY), config.get(KEY_PASSPHRASE), timeout(config));
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

    @Override
    public String toString()
    {
        return "ElasticsearchSettings[hosts=" + hosts + ", authentication=" + authentication + ", user=" + user + ", verificationMode="
                + verificationMode + ", certificate=" + certificate + "]";
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
