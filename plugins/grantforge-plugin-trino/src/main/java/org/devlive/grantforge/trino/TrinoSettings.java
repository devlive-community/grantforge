// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino;

import org.devlive.grantforge.plugin.api.KerberosRealms;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * The settings of a Trino service, checked, and the driver properties they make. Field names are the Trino driver's own
 * properties where it has one ({@code user}, {@code accessToken}, {@code SSL}, {@code SSLTrustStorePath},
 * {@code KerberosRemoteServiceName}…). Credentials never go in the URL, which may hold anything else the driver reads; a
 * server signs users in over TLS only, so a sign-in other than none needs it.
 *
 * @param url the JDBC URL, {@code jdbc:trino://host:port}
 * @param authentication none, password, jwt or kerberos
 * @param user the user, or with Kerberos the principal
 * @param password the user's password or the principal's Kerberos password, or {@code null}
 * @param accessToken the JSON web token, or {@code null}
 * @param keytab the principal's keytab on the GrantForge server, or {@code null}
 * @param kdcs the KDCs of the principal's realm, or {@code null} for the server's krb5.conf
 * @param serviceName the first part of the coordinator's Kerberos principal
 * @param canonicalHost whether the coordinator's principal takes its host's canonical name rather than the URL's
 * @param tls whether the driver speaks TLS
 * @param truststore the truststore that verifies the coordinator, or {@code null} for the JDK's
 * @param truststorePassword its password, or {@code null}
 * @param truststoreType JKS or PKCS12
 * @param verification FULL, CA or NONE
 * @param timeout the request timeout in milliseconds
 */
record TrinoSettings(String url, String authentication, String user, @Nullable String password, @Nullable String accessToken,
        @Nullable String keytab, @Nullable String kdcs, String serviceName, boolean canonicalHost, boolean tls, @Nullable String truststore,
        @Nullable String truststorePassword, String truststoreType, String verification, int timeout)
{
    static final String URL = "jdbc.url";
    static final String AUTHENTICATION = "authentication";
    static final String USER = "user";
    static final String PASSWORD = "password";
    static final String ACCESS_TOKEN = "accessToken";
    static final String KEYTAB = "keytab";
    static final String KDC = "kerberos.kdc";
    static final String SERVICE_NAME = "KerberosRemoteServiceName";
    static final String CANONICAL_HOST = "KerberosUseCanonicalHostname";
    static final String SSL = "SSL";
    static final String TRUSTSTORE = "SSLTrustStorePath";
    static final String TRUSTSTORE_PASSWORD = "SSLTrustStorePassword";
    static final String TRUSTSTORE_TYPE = "SSLTrustStoreType";
    static final String VERIFICATION = "SSLVerification";
    static final String TIMEOUT = "timeout";

    static final String NONE = "none";
    static final String PASSWORD_AUTH = "password";
    static final String JWT = "jwt";
    static final String KERBEROS = "kerberos";
    static final List<String> AUTHENTICATIONS = List.of(NONE, PASSWORD_AUTH, JWT, KERBEROS);
    static final List<String> STORE_TYPES = List.of("JKS", "PKCS12");
    static final List<String> VERIFICATIONS = List.of("FULL", "CA", "NONE");
    static final int DEFAULT_TIMEOUT = 30_000;

    /** The URL parameters that would sign in other than as the service says, or read secrets on the GrantForge server. */
    private static final Set<String> FORBIDDEN = Set.of("user", "password", "accesstoken", "sessionuser", "extracredentials",
            "externalauthentication", "oauth2clientid", "oauth2clientsecret", "oauth2tokenendpoint", "kerberosprincipal", "kerberoskeytabpath",
            "kerberoscredentialcachepath", "kerberosconfigpath", "kerberosdelegation", "kerberosconstraineddelegation", "ssltruststorepassword",
            "sslkeystorepassword", "sslkeystorepath");

    /**
     * Checks a service's settings.
     *
     * @param config the settings
     * @return what is wrong, by field; empty when they are usable
     */
    static List<ConfigProblem> problems(ServiceConfig config)
    {
        List<ConfigProblem> problems = new ArrayList<>();
        String url = text(config, URL);
        boolean tlsInUrl = false;
        if (url == null) {
            problems.add(new ConfigProblem(URL, ConfigProblem.Reason.REQUIRED, "name the coordinator, such as jdbc:trino://trino.example.com:8443"));
        }
        else {
            String wrong = urlProblem(url);
            if (wrong != null) {
                problems.add(invalid(URL, wrong));
            }
            tlsInUrl = parameters(url).stream().anyMatch(parameter -> "ssl=true".equalsIgnoreCase(parameter));
        }
        String authentication = lower(config, AUTHENTICATION, NONE);
        if (!AUTHENTICATIONS.contains(authentication)) {
            problems.add(invalid(AUTHENTICATION, "authentication is none, password, jwt or kerberos"));
        }
        String user = text(config, USER);
        if (user == null) {
            problems.add(new ConfigProblem(USER, ConfigProblem.Reason.REQUIRED, "name the user that looks up catalogs and tables"));
        }
        boolean tls = tlsInUrl || "true".equalsIgnoreCase(textOr(config, SSL, "false"));
        String ssl = text(config, SSL);
        if (ssl != null && !"true".equalsIgnoreCase(ssl) && !"false".equalsIgnoreCase(ssl)) {
            problems.add(invalid(SSL, "SSL is true or false"));
        }
        if (!NONE.equals(authentication) && AUTHENTICATIONS.contains(authentication) && !tls) {
            problems.add(invalid(SSL, "Trino signs users in over TLS only; set SSL to true"));
        }
        credentials(config, authentication, user, problems);
        if (!tls) {
            for (String field : List.of(TRUSTSTORE, TRUSTSTORE_PASSWORD, VERIFICATION)) {
                if (text(config, field) != null) {
                    problems.add(invalid(field, "only TLS uses " + field));
                }
            }
        }
        if (!STORE_TYPES.contains(upper(config, TRUSTSTORE_TYPE, "JKS"))) {
            problems.add(invalid(TRUSTSTORE_TYPE, "SSLTrustStoreType is JKS or PKCS12"));
        }
        if (!VERIFICATIONS.contains(upper(config, VERIFICATION, "FULL"))) {
            problems.add(invalid(VERIFICATION, "SSLVerification is FULL, CA or NONE"));
        }
        // Trino's driver refuses a truststore it would not use.
        if (tls && "NONE".equals(upper(config, VERIFICATION, "FULL"))) {
            for (String field : List.of(TRUSTSTORE, TRUSTSTORE_PASSWORD)) {
                if (text(config, field) != null) {
                    problems.add(invalid(field, "SSLVerification NONE checks no certificate, so it takes no " + field));
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

    private static void credentials(ServiceConfig config, String authentication, @Nullable String user, List<ConfigProblem> problems)
    {
        List<String> allowed = switch (authentication) {
            case PASSWORD_AUTH -> List.of(PASSWORD);
            case JWT -> List.of(ACCESS_TOKEN);
            case KERBEROS -> List.of(PASSWORD, KEYTAB, KDC, SERVICE_NAME, CANONICAL_HOST);
            default -> List.of();
        };
        for (String field : List.of(PASSWORD, ACCESS_TOKEN, KEYTAB, KDC, SERVICE_NAME, CANONICAL_HOST)) {
            if (!allowed.contains(field) && text(config, field) != null) {
                problems.add(invalid(field, authentication + " authentication does not use " + field));
            }
        }
        if (PASSWORD_AUTH.equals(authentication) && text(config, PASSWORD) == null) {
            problems.add(new ConfigProblem(PASSWORD, ConfigProblem.Reason.REQUIRED, "password authentication needs the user's password"));
        }
        if (JWT.equals(authentication) && text(config, ACCESS_TOKEN) == null) {
            problems.add(new ConfigProblem(ACCESS_TOKEN, ConfigProblem.Reason.REQUIRED, "jwt authentication needs the token"));
        }
        if (!KERBEROS.equals(authentication)) {
            return;
        }
        String canonical = text(config, CANONICAL_HOST);
        if (canonical != null && !"true".equalsIgnoreCase(canonical) && !"false".equalsIgnoreCase(canonical)) {
            problems.add(invalid(CANONICAL_HOST, "KerberosUseCanonicalHostname is true or false"));
        }
        if (user != null) {
            try {
                KerberosRealms.realm(user);
            }
            catch (IllegalArgumentException wrong) {
                problems.add(invalid(USER, String.valueOf(wrong.getMessage())));
            }
        }
        if (text(config, PASSWORD) == null && text(config, KEYTAB) == null) {
            problems.add(new ConfigProblem(PASSWORD, ConfigProblem.Reason.REQUIRED, "Kerberos needs the principal's password or a keytab"));
        }
        String kdcs = text(config, KDC);
        if (kdcs != null) {
            try {
                KerberosRealms.kdcs(kdcs);
            }
            catch (IllegalArgumentException wrong) {
                problems.add(invalid(KDC, String.valueOf(wrong.getMessage())));
            }
        }
    }

    /** What is wrong with a URL, or {@code null}. */
    private static @Nullable String urlProblem(String url)
    {
        if (!url.startsWith("jdbc:trino://")) {
            return "the URL starts with jdbc:trino://, such as jdbc:trino://trino.example.com:8443";
        }
        URI uri;
        try {
            uri = new URI(url.substring("jdbc:".length()));
        }
        catch (URISyntaxException wrong) {
            return "the URL is not valid: " + wrong.getReason();
        }
        if (uri.getHost() == null) {
            return "the URL names no host";
        }
        for (String parameter : parameters(url)) {
            int equals = parameter.indexOf('=');
            String name = (equals < 0 ? parameter : parameter.substring(0, equals)).toLowerCase(Locale.ROOT);
            if (FORBIDDEN.contains(name)) {
                return "the URL may not set " + (equals < 0 ? parameter : parameter.substring(0, equals)) + "; use the service's fields";
            }
        }
        return null;
    }

    private static List<String> parameters(String url)
    {
        int query = url.indexOf('?');
        if (query < 0 || query == url.length() - 1) {
            return List.of();
        }
        return List.of(url.substring(query + 1).split("&"));
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
    static TrinoSettings of(ServiceConfig config)
    {
        List<ConfigProblem> problems = problems(config);
        if (!problems.isEmpty()) {
            ConfigProblem first = problems.get(0);
            throw new IllegalArgumentException(first.field() + ": " + first.detail());
        }
        String url = String.valueOf(text(config, URL));
        boolean tls = "true".equalsIgnoreCase(textOr(config, SSL, "false"))
                || parameters(url).stream().anyMatch(parameter -> "ssl=true".equalsIgnoreCase(parameter));
        return new TrinoSettings(url, lower(config, AUTHENTICATION, NONE), String.valueOf(text(config, USER)), text(config, PASSWORD),
                text(config, ACCESS_TOKEN), text(config, KEYTAB), text(config, KDC), textOr(config, SERVICE_NAME, "trino"),
                !"false".equalsIgnoreCase(textOr(config, CANONICAL_HOST, "true")), tls,
                text(config, TRUSTSTORE), text(config, TRUSTSTORE_PASSWORD), upper(config, TRUSTSTORE_TYPE, "JKS"),
                upper(config, VERIFICATION, "FULL"), timeout(config));
    }

    /**
     * Reads the request timeout.
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

    /**
     * Returns whether the sign-in is Kerberos.
     *
     * @return {@code true} if it is
     */
    boolean kerberos()
    {
        return KERBEROS.equals(authentication);
    }

    /**
     * Builds the driver's properties, but for a Kerberos credential, which the client adds.
     *
     * @return the properties
     */
    Properties driver()
    {
        Properties properties = new Properties();
        properties.setProperty(USER, kerberos() ? sessionUser(user) : user);
        properties.setProperty("source", "grantforge");
        properties.setProperty(TIMEOUT, timeout + "ms");
        if (PASSWORD_AUTH.equals(authentication) && password != null) {
            properties.setProperty(PASSWORD, password);
        }
        if (JWT.equals(authentication) && accessToken != null) {
            properties.setProperty(ACCESS_TOKEN, accessToken);
        }
        if (kerberos()) {
            properties.setProperty(SERVICE_NAME, serviceName);
            properties.setProperty(CANONICAL_HOST, String.valueOf(canonicalHost));
        }
        if (tls) {
            properties.setProperty(SSL, "true");
            properties.setProperty(VERIFICATION, verification);
            if (truststore != null) {
                properties.setProperty(TRUSTSTORE, truststore);
                properties.setProperty(TRUSTSTORE_TYPE, truststoreType);
            }
            if (truststorePassword != null) {
                properties.setProperty(TRUSTSTORE_PASSWORD, truststorePassword);
            }
        }
        return properties;
    }

    /**
     * Returns the session user of a Kerberos principal, as Trino maps it by default: its name before the realm.
     *
     * @param principal such as {@code grantforge@EXAMPLE.COM}
     * @return such as {@code grantforge}
     */
    static String sessionUser(String principal)
    {
        int realm = principal.indexOf('@');
        return realm < 0 ? principal : principal.substring(0, realm);
    }

    private static @Nullable String text(ServiceConfig config, String field)
    {
        String value = config.get(field);
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String textOr(ServiceConfig config, String field, String fallback)
    {
        String value = text(config, field);
        return value == null ? fallback : value;
    }

    private static String lower(ServiceConfig config, String field, String fallback)
    {
        return textOr(config, field, fallback).toLowerCase(Locale.ROOT);
    }

    private static String upper(ServiceConfig config, String field, String fallback)
    {
        return textOr(config, field, fallback).toUpperCase(Locale.ROOT);
    }
}
