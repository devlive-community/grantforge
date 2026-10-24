// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr;

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
 * The settings of a Solr service, checked. The nodes are named by their base URL, such as
 * {@code https://solr1.example.com:8983/solr}, as SolrJ takes them; naming several makes a lookup go to the next node
 * when one cannot be reached. The client signs in with basic authentication or a JSON web token, or not at all, and
 * over https trusts the certificates of a trust store of its own, named as Java's {@code javax.net.ssl.*} settings name
 * them.
 *
 * @param urls the nodes' base URLs, without a trailing slash
 * @param authentication none, basic or jwt
 * @param user the user name, for basic
 * @param password the password, for basic
 * @param token the token, for jwt
 * @param trustStore the trust store, or {@code null} for the JVM's
 * @param trustStorePassword the trust store's password, or {@code null}
 * @param trustStoreType the trust store's type, such as PKCS12
 * @param timeout how long a request may take, in milliseconds
 */
record SolrSettings(List<String> urls, String authentication, @Nullable String user, @Nullable String password, @Nullable String token,
        @Nullable String trustStore, @Nullable String trustStorePassword, String trustStoreType, int timeout)
{
    static final String URLS = "solr.urls";
    static final String AUTHENTICATION = "authentication";
    static final String USER = "username";
    static final String PASSWORD = "password";
    static final String TOKEN_FIELD = "token";
    static final String TRUST_STORE = "javax.net.ssl.trustStore";
    static final String TRUST_STORE_PASSWORD = "javax.net.ssl.trustStorePassword";
    static final String TRUST_STORE_TYPE = "javax.net.ssl.trustStoreType";
    static final String TIMEOUT = "timeout";

    static final String NONE = "none";
    static final String BASIC = "basic";
    static final String JWT = "jwt";
    static final List<String> AUTHENTICATIONS = List.of(NONE, BASIC, JWT);
    static final String DEFAULT_TRUST_STORE_TYPE = "PKCS12";
    static final int DEFAULT_TIMEOUT = 30_000;

    /** The fields each sign-in uses; the others must stay empty. */
    private static final Map<String, List<String>> SIGN_IN_FIELDS = Map.of(NONE, List.of(), BASIC, List.of(USER, PASSWORD), JWT, List.of(TOKEN_FIELD));

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
        if (urls == null) {
            problems.add(new ConfigProblem(URLS, ConfigProblem.Reason.REQUIRED,
                    "name a node's base URL, such as http://solr1.example.com:8983/solr, or several separated by commas"));
        }
        else if (urls(urls).isEmpty()) {
            problems.add(invalid(URLS, "list the nodes as http:// or https:// base URLs separated by commas, such as"
                    + " http://solr1:8983/solr,http://solr2:8983/solr"));
        }
        String authentication = lower(config, AUTHENTICATION, NONE);
        List<String> used = SIGN_IN_FIELDS.get(authentication);
        if (used == null) {
            problems.add(invalid(AUTHENTICATION, "authentication is none, basic or jwt"));
            used = List.of();
        }
        for (Map.Entry<String, List<String>> signIn : SIGN_IN_FIELDS.entrySet()) {
            for (String field : signIn.getValue()) {
                if (!used.contains(field) && text(config, field) != null) {
                    problems.add(invalid(field, "only " + signIn.getKey() + " authentication uses " + field));
                }
            }
        }
        for (String field : used) {
            if (text(config, field) == null) {
                problems.add(new ConfigProblem(field, ConfigProblem.Reason.REQUIRED, authentication + " authentication needs " + field));
            }
        }
        if (text(config, TRUST_STORE) == null && (text(config, TRUST_STORE_PASSWORD) != null || text(config, TRUST_STORE_TYPE) != null)) {
            problems.add(invalid(TRUST_STORE, "name the trust store its password and type are for"));
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
     * @param value base URLs separated by commas
     * @return them without trailing slashes, or none when one is no http or https URL with a host
     */
    static List<String> urls(String value)
    {
        List<String> urls = new ArrayList<>();
        for (String entry : value.split(",", -1)) {
            String url = entry.strip();
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            if (!node(url)) {
                return List.of();
            }
            if (!urls.contains(url)) {
                urls.add(url);
            }
        }
        return urls;
    }

    /** Whether a value is an http or https URL with a host, and neither a query nor a fragment. */
    private static boolean node(String url)
    {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return ("http".equals(scheme) || "https".equals(scheme)) && uri.getHost() != null && uri.getQuery() == null && uri.getFragment() == null;
        }
        catch (URISyntaxException wrong) {
            return false;
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
    static SolrSettings of(ServiceConfig config)
    {
        List<ConfigProblem> problems = problems(config);
        if (!problems.isEmpty()) {
            ConfigProblem first = problems.get(0);
            throw new IllegalArgumentException(first.field() + ": " + first.detail());
        }
        String type = text(config, TRUST_STORE_TYPE);
        return new SolrSettings(List.copyOf(urls(String.valueOf(text(config, URLS)))), lower(config, AUTHENTICATION, NONE), text(config, USER),
                text(config, PASSWORD), text(config, TOKEN_FIELD), text(config, TRUST_STORE), text(config, TRUST_STORE_PASSWORD),
                type == null ? DEFAULT_TRUST_STORE_TYPE : type, timeout(config));
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

    @Override
    public String toString()
    {
        return "SolrSettings[" + urls + ", " + authentication + "]";
    }
}
