// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar;

import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.jspecify.annotations.Nullable;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The settings of a Pulsar service, checked. Field names are those of Pulsar's own {@code client.conf} where it has one
 * ({@code webServiceUrl}, {@code tlsTrustCertsFilePath}, {@code tlsCertificateFilePath}…). The admin client signs in
 * with a token, a user name and password, OAuth 2.0 client credentials or a TLS client certificate, or not at all.
 * Naming several brokers makes the client go to the next when one cannot be reached.
 *
 * @param webServiceUrl the brokers' web service, as Pulsar's admin client takes it: {@code http://b1:8080,b2:8080}
 * @param authentication none, token, basic, oauth2 or tls
 * @param token the token, for token
 * @param user the user name, for basic
 * @param password the password, for basic
 * @param issuerUrl the OAuth 2.0 issuer, for oauth2
 * @param credentialsUrl the OAuth 2.0 credentials file, for oauth2
 * @param audience the OAuth 2.0 audience, for oauth2, or {@code null}
 * @param scope the OAuth 2.0 scope, for oauth2, or {@code null}
 * @param certificate the client certificate, for tls
 * @param key the client certificate's key, for tls
 * @param trustCerts the certificates trusted for https, or {@code null} for the JVM's
 * @param verifyHostname whether a broker's certificate must name its host
 * @param timeout how long a request may take, in milliseconds
 */
record PulsarSettings(String webServiceUrl, String authentication, @Nullable String token, @Nullable String user, @Nullable String password,
        @Nullable URL issuerUrl, @Nullable URL credentialsUrl, @Nullable String audience, @Nullable String scope, @Nullable String certificate,
        @Nullable String key, @Nullable String trustCerts, boolean verifyHostname, int timeout)
{
    static final String WEB_SERVICE_URL = "webServiceUrl";
    static final String AUTHENTICATION = "authentication";
    static final String TOKEN_FIELD = "token";
    static final String USER = "username";
    static final String PASSWORD = "password";
    static final String ISSUER_URL = "issuerUrl";
    static final String PRIVATE_KEY = "privateKey";
    static final String AUDIENCE = "audience";
    static final String SCOPE = "scope";
    static final String CERTIFICATE = "tlsCertificateFilePath";
    static final String KEY = "tlsKeyFilePath";
    static final String TRUST_CERTS = "tlsTrustCertsFilePath";
    static final String HOSTNAME_VERIFICATION = "tlsEnableHostnameVerification";
    static final String TIMEOUT = "timeout";

    static final String NONE = "none";
    static final String TOKEN = "token";
    static final String BASIC = "basic";
    static final String OAUTH2 = "oauth2";
    static final String TLS = "tls";
    static final List<String> AUTHENTICATIONS = List.of(NONE, TOKEN, BASIC, OAUTH2, TLS);
    static final int DEFAULT_PORT = 8080;
    static final int DEFAULT_TLS_PORT = 8443;
    static final int DEFAULT_TIMEOUT = 30_000;

    /** The fields each sign-in uses; the others must stay empty. */
    private static final Map<String, List<String>> SIGN_IN_FIELDS = Map.of(NONE, List.of(), TOKEN, List.of(TOKEN_FIELD), BASIC, List.of(USER, PASSWORD),
            OAUTH2, List.of(ISSUER_URL, PRIVATE_KEY, AUDIENCE, SCOPE), TLS, List.of(CERTIFICATE, KEY));

    private static final Pattern URL_ENTRY = Pattern.compile("(?:(https?)://)?(\\[[0-9A-Fa-f:.]+]|[A-Za-z0-9._-]+)(?::(\\d{1,5}))?/?",
            Pattern.CASE_INSENSITIVE);

    /**
     * Checks a service's settings.
     *
     * @param config the settings
     * @return what is wrong, by field; empty when they are usable
     */
    static List<ConfigProblem> problems(ServiceConfig config)
    {
        List<ConfigProblem> problems = new ArrayList<>();
        String url = text(config, WEB_SERVICE_URL);
        if (url == null) {
            problems.add(new ConfigProblem(WEB_SERVICE_URL, ConfigProblem.Reason.REQUIRED,
                    "name the brokers' web service, such as http://broker1.example.com:8080, or several brokers separated by commas"));
        }
        else if (serviceUrl(url) == null) {
            problems.add(invalid(WEB_SERVICE_URL, "list the brokers as http://host:port or https://host:port, several separated by commas, such as"
                    + " http://broker1:8080,broker2:8080"));
        }
        String authentication = lower(config, AUTHENTICATION, NONE);
        List<String> used = SIGN_IN_FIELDS.get(authentication);
        if (used == null) {
            problems.add(invalid(AUTHENTICATION, "authentication is none, token, basic, oauth2 or tls"));
            used = List.of();
        }
        for (Map.Entry<String, List<String>> signIn : SIGN_IN_FIELDS.entrySet()) {
            for (String field : signIn.getValue()) {
                if (!used.contains(field) && text(config, field) != null) {
                    problems.add(invalid(field, "only " + signIn.getKey() + " authentication uses " + field));
                }
            }
        }
        switch (authentication) {
            case TOKEN -> required(config, TOKEN_FIELD, "token authentication needs the token", problems);
            case BASIC -> {
                required(config, USER, "basic authentication needs the user name", problems);
                required(config, PASSWORD, "basic authentication needs the password", problems);
            }
            case OAUTH2 -> oauth2(config, problems);
            case TLS -> {
                required(config, CERTIFICATE, "tls authentication needs the client certificate", problems);
                required(config, KEY, "tls authentication needs the client certificate's key", problems);
            }
            default -> {
                // Nothing to sign in with.
            }
        }
        String verify = lower(config, HOSTNAME_VERIFICATION, "true");
        if (!"true".equals(verify) && !"false".equals(verify)) {
            problems.add(invalid(HOSTNAME_VERIFICATION, "tlsEnableHostnameVerification is true or false"));
        }
        try {
            timeout(config);
        }
        catch (IllegalArgumentException wrong) {
            problems.add(invalid(TIMEOUT, String.valueOf(wrong.getMessage())));
        }
        return problems;
    }

    private static void oauth2(ServiceConfig config, List<ConfigProblem> problems)
    {
        String issuer = text(config, ISSUER_URL);
        if (issuer == null) {
            problems.add(new ConfigProblem(ISSUER_URL, ConfigProblem.Reason.REQUIRED, "oauth2 authentication needs the issuer's URL"));
        }
        else if (issuer(issuer) == null) {
            problems.add(invalid(ISSUER_URL, "the issuer is an https:// or http:// URL, such as https://auth.example.com/"));
        }
        String credentials = text(config, PRIVATE_KEY);
        if (credentials == null) {
            problems.add(new ConfigProblem(PRIVATE_KEY, ConfigProblem.Reason.REQUIRED,
                    "oauth2 authentication needs the credentials file, with the client's client_id and client_secret"));
        }
        else if (credentials(credentials) == null) {
            problems.add(invalid(PRIVATE_KEY, "the credentials are a file on the GrantForge server, by its path or a file: URL"));
        }
    }

    private static void required(ServiceConfig config, String field, String message, List<ConfigProblem> problems)
    {
        if (text(config, field) == null) {
            problems.add(new ConfigProblem(field, ConfigProblem.Reason.REQUIRED, message));
        }
    }

    /**
     * Turns a list of brokers into the service URL Pulsar's admin client takes: one scheme, then every broker with its port.
     *
     * @param value such as {@code http://b1:8080,http://b2:8080} or {@code https://b1,b2}
     * @return such as {@code http://b1:8080,b2:8080}, or {@code null} when it is not a list of brokers of one scheme
     */
    static @Nullable String serviceUrl(String value)
    {
        String scheme = null;
        List<String> brokers = new ArrayList<>();
        for (String entry : value.split(",", -1)) {
            Matcher match = URL_ENTRY.matcher(entry.strip());
            if (!match.matches()) {
                return null;
            }
            String named = match.group(1) == null ? null : match.group(1).toLowerCase(Locale.ROOT);
            if (scheme == null) {
                if (named == null) {
                    return null;
                }
                scheme = named;
            }
            else if (named != null && !named.equals(scheme)) {
                return null;
            }
            int port = match.group(3) == null ? "https".equals(scheme) ? DEFAULT_TLS_PORT : DEFAULT_PORT : port(match.group(3));
            if (port == 0) {
                return null;
            }
            String broker = match.group(2) + ":" + port;
            if (!brokers.contains(broker)) {
                brokers.add(broker);
            }
        }
        return scheme + "://" + String.join(",", brokers);
    }

    private static int port(String value)
    {
        int port = Integer.parseInt(value);
        return port >= 1 && port <= 65_535 ? port : 0;
    }

    private static @Nullable URL issuer(String value)
    {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return ("https".equals(scheme) || "http".equals(scheme)) && uri.getHost() != null ? uri.toURL() : null;
        }
        catch (URISyntaxException | MalformedURLException | IllegalArgumentException wrong) {
            return null;
        }
    }

    /** The credentials file, by its path or a file: URL, as Pulsar's OAuth 2.0 client reads it. */
    private static @Nullable URL credentials(String value)
    {
        try {
            if (value.regionMatches(true, 0, "file:", 0, 5)) {
                URI uri = new URI(value);
                return uri.getPath() == null || uri.getPath().isEmpty() ? null : uri.toURL();
            }
            Path path = Path.of(value);
            return path.isAbsolute() ? path.toUri().toURL() : null;
        }
        catch (URISyntaxException | MalformedURLException | IllegalArgumentException wrong) {
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
    static PulsarSettings of(ServiceConfig config)
    {
        List<ConfigProblem> problems = problems(config);
        if (!problems.isEmpty()) {
            ConfigProblem first = problems.get(0);
            throw new IllegalArgumentException(first.field() + ": " + first.detail());
        }
        String authentication = lower(config, AUTHENTICATION, NONE);
        boolean oauth2 = OAUTH2.equals(authentication);
        String issuer = text(config, ISSUER_URL);
        String credentials = text(config, PRIVATE_KEY);
        return new PulsarSettings(String.valueOf(serviceUrl(String.valueOf(text(config, WEB_SERVICE_URL)))), authentication, text(config, TOKEN_FIELD),
                text(config, USER), text(config, PASSWORD), oauth2 && issuer != null ? issuer(issuer) : null,
                oauth2 && credentials != null ? credentials(credentials) : null, text(config, AUDIENCE), text(config, SCOPE), text(config, CERTIFICATE),
                text(config, KEY), text(config, TRUST_CERTS), Boolean.parseBoolean(lower(config, HOSTNAME_VERIFICATION, "true")), timeout(config));
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
        return "PulsarSettings[" + webServiceUrl + ", " + authentication + "]";
    }
}
