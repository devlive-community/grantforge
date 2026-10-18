// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.config.types.Password;
import org.devlive.grantforge.plugin.api.KerberosRealms;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The settings of a Kafka service, checked, and the AdminClient settings they make. Field names are Kafka's own client
 * settings where Kafka has one ({@code bootstrap.servers}, {@code security.protocol}, {@code sasl.mechanism},
 * {@code ssl.truststore.location}…); the sign-in has fields of its own, so no JAAS text is ever typed: PLAIN and SCRAM
 * take a user and its password, Kerberos a principal with a keytab or its password.
 *
 * @param servers the bootstrap servers, {@code host:port} separated by commas
 * @param protocol {@code PLAINTEXT}, {@code SSL}, {@code SASL_PLAINTEXT} or {@code SASL_SSL}
 * @param mechanism the SASL mechanism, or {@code null} without SASL
 * @param user the SASL user or Kerberos principal, or {@code null} without SASL
 * @param password the user's or principal's password, or {@code null}
 * @param keytab the principal's keytab on the GrantForge server, or {@code null}
 * @param kdcs the KDCs of the principal's realm, or {@code null} for the server's krb5.conf
 * @param serviceName the brokers' Kerberos service name
 * @param truststore the TLS truststore, or {@code null} for the JDK's
 * @param truststorePassword its password, or {@code null}
 * @param truststoreType JKS, PKCS12 or PEM
 * @param keystore the client certificate's keystore for brokers that ask for one, or {@code null}
 * @param keystorePassword its password, or {@code null}
 * @param keyPassword the key's password, or {@code null} for the keystore's
 * @param verifyHostname whether the brokers' certificates must name their hosts
 * @param timeout the request timeout in milliseconds
 */
record KafkaSettings(String servers, String protocol, @Nullable String mechanism, @Nullable String user, @Nullable String password,
        @Nullable String keytab, @Nullable String kdcs, String serviceName, @Nullable String truststore, @Nullable String truststorePassword,
        String truststoreType, @Nullable String keystore, @Nullable String keystorePassword, @Nullable String keyPassword,
        boolean verifyHostname, int timeout)
{
    static final String SERVERS = CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG;
    static final String PROTOCOL = CommonClientConfigs.SECURITY_PROTOCOL_CONFIG;
    static final String MECHANISM = SaslConfigs.SASL_MECHANISM;
    static final String USER = "username";
    static final String PASSWORD = "password";
    static final String KEYTAB = "keytab";
    static final String KDC = "kerberos.kdc";
    static final String SERVICE_NAME = SaslConfigs.SASL_KERBEROS_SERVICE_NAME;
    static final String TRUSTSTORE = SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG;
    static final String TRUSTSTORE_PASSWORD = SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG;
    static final String TRUSTSTORE_TYPE = SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG;
    static final String KEYSTORE = SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG;
    static final String KEYSTORE_PASSWORD = SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG;
    static final String KEY_PASSWORD = SslConfigs.SSL_KEY_PASSWORD_CONFIG;
    static final String VERIFY_HOSTNAME = "ssl.verify.hostname";
    static final String TIMEOUT = CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG;

    static final List<String> PROTOCOLS = List.of("PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL");
    static final List<String> MECHANISMS = List.of("PLAIN", "SCRAM-SHA-256", "SCRAM-SHA-512", "GSSAPI");
    static final List<String> STORE_TYPES = List.of("JKS", "PKCS12", "PEM");
    static final String GSSAPI = "GSSAPI";
    static final int DEFAULT_TIMEOUT = 30_000;

    /** The JAAS option that carries a Kerberos password to {@link KerberosPasswordHandler}, never to Kerberos itself. */
    static final String PASSWORD_OPTION = "grantforge.password";

    private static final Pattern SERVER = Pattern.compile("(?:\\[[0-9A-Fa-f:.]+]|[A-Za-z0-9._-]+):(\\d{1,5})");
    private static final Set<String> SSL_FIELDS = Set.of(TRUSTSTORE, TRUSTSTORE_PASSWORD, KEYSTORE, KEYSTORE_PASSWORD, KEY_PASSWORD);

    /**
     * Checks a service's settings.
     *
     * @param config the settings
     * @return what is wrong, by field; empty when they are usable
     */
    static List<ConfigProblem> problems(ServiceConfig config)
    {
        List<ConfigProblem> problems = new ArrayList<>();
        String servers = text(config, SERVERS);
        if (servers != null) {
            for (String server : servers.split(",", -1)) {
                if (!validServer(server.strip())) {
                    problems.add(invalid(SERVERS, "list the brokers as host:port separated by commas, such as kafka1:9092,kafka2:9092"));
                    break;
                }
            }
        }
        String protocol = upper(config, PROTOCOL, "PLAINTEXT");
        if (!PROTOCOLS.contains(protocol)) {
            problems.add(invalid(PROTOCOL, "security.protocol must be one of " + String.join(", ", PROTOCOLS)));
        }
        boolean sasl = protocol.startsWith("SASL_");
        String mechanism = upper(config, MECHANISM, "");
        if (sasl) {
            sasl(config, mechanism, problems);
        }
        else {
            for (String field : List.of(MECHANISM, USER, PASSWORD, KEYTAB, KDC)) {
                if (text(config, field) != null) {
                    problems.add(invalid(field, "only SASL_PLAINTEXT and SASL_SSL sign in; choose one of them to use " + field));
                }
            }
        }
        if (!protocol.endsWith("SSL")) {
            for (String field : SSL_FIELDS) {
                if (text(config, field) != null) {
                    problems.add(invalid(field, "only SSL and SASL_SSL use " + field));
                }
            }
        }
        String type = upper(config, TRUSTSTORE_TYPE, "JKS");
        if (!STORE_TYPES.contains(type)) {
            problems.add(invalid(TRUSTSTORE_TYPE, "ssl.truststore.type must be one of " + String.join(", ", STORE_TYPES)));
        }
        String verify = text(config, VERIFY_HOSTNAME);
        if (verify != null && !"true".equalsIgnoreCase(verify) && !"false".equalsIgnoreCase(verify)) {
            problems.add(invalid(VERIFY_HOSTNAME, "ssl.verify.hostname is true or false"));
        }
        try {
            timeout(config);
        }
        catch (IllegalArgumentException wrong) {
            problems.add(invalid(TIMEOUT, String.valueOf(wrong.getMessage())));
        }
        return problems;
    }

    private static void sasl(ServiceConfig config, String mechanism, List<ConfigProblem> problems)
    {
        if (!MECHANISMS.contains(mechanism)) {
            problems.add(new ConfigProblem(MECHANISM, mechanism.isEmpty() ? ConfigProblem.Reason.REQUIRED : ConfigProblem.Reason.INVALID,
                    "sasl.mechanism must be one of " + String.join(", ", MECHANISMS)));
            return;
        }
        String user = text(config, USER);
        if (user == null) {
            problems.add(new ConfigProblem(USER, ConfigProblem.Reason.REQUIRED, "SASL signs in as a user; name it"));
        }
        if (!GSSAPI.equals(mechanism)) {
            if (text(config, PASSWORD) == null) {
                problems.add(new ConfigProblem(PASSWORD, ConfigProblem.Reason.REQUIRED, mechanism + " signs in with the user's password"));
            }
            for (String field : List.of(KEYTAB, KDC)) {
                if (text(config, field) != null) {
                    problems.add(invalid(field, "only GSSAPI (Kerberos) uses a keytab and KDCs"));
                }
            }
            return;
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

    private static boolean validServer(String server)
    {
        java.util.regex.Matcher match = SERVER.matcher(server);
        if (!match.matches()) {
            return false;
        }
        int port = Integer.parseInt(match.group(1));
        return port >= 1 && port <= 65_535;
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
    static KafkaSettings of(ServiceConfig config)
    {
        List<ConfigProblem> problems = problems(config);
        String servers = text(config, SERVERS);
        if (servers == null) {
            throw new IllegalArgumentException("bootstrap.servers is required");
        }
        if (!problems.isEmpty()) {
            ConfigProblem first = problems.get(0);
            throw new IllegalArgumentException(first.field() + ": " + first.detail());
        }
        String protocol = upper(config, PROTOCOL, "PLAINTEXT");
        boolean sasl = protocol.startsWith("SASL_");
        return new KafkaSettings(servers.replace(" ", ""), protocol, sasl ? upper(config, MECHANISM, "") : null, sasl ? text(config, USER) : null,
                sasl ? text(config, PASSWORD) : null, sasl ? text(config, KEYTAB) : null, sasl ? text(config, KDC) : null,
                textOr(config, SERVICE_NAME, "kafka"), text(config, TRUSTSTORE), text(config, TRUSTSTORE_PASSWORD), upper(config, TRUSTSTORE_TYPE, "JKS"),
                text(config, KEYSTORE), text(config, KEYSTORE_PASSWORD), text(config, KEY_PASSWORD),
                !"false".equalsIgnoreCase(textOr(config, VERIFY_HOSTNAME, "true")), timeout(config));
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
        throw new IllegalArgumentException("request.timeout.ms is a whole number from 1 to 600000");
    }

    /**
     * Returns whether the sign-in is Kerberos.
     *
     * @return {@code true} for GSSAPI
     */
    boolean kerberos()
    {
        return GSSAPI.equals(mechanism);
    }

    /**
     * Builds the AdminClient settings. Kerberos realms whose KDCs are given are declared for the process first.
     *
     * @param passwordHandler the login callback handler that answers Kerberos password prompts
     * @param clientId the client's id, distinct per client, as Kafka registers it by id
     * @return the settings
     * @throws IOException if the Kerberos configuration of the process cannot be written
     */
    Map<String, Object> client(Class<?> passwordHandler, String clientId) throws IOException
    {
        Map<String, Object> client = new HashMap<>();
        client.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        client.put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, protocol);
        client.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, timeout);
        client.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, timeout);
        client.put(AdminClientConfig.CLIENT_ID_CONFIG, clientId);
        // Nobody reads the lookup client's metrics: no JMX reporter.
        client.put(AdminClientConfig.METRIC_REPORTER_CLASSES_CONFIG, "");
        String mechanismName = mechanism;
        if (mechanismName != null) {
            client.put(SaslConfigs.SASL_MECHANISM, mechanismName);
            client.put(SaslConfigs.SASL_JAAS_CONFIG, new Password(jaas()));
            if (kerberos()) {
                client.put(SaslConfigs.SASL_KERBEROS_SERVICE_NAME, serviceName);
                if (keytab == null) {
                    client.put(SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS, passwordHandler);
                }
                String realmKdcs = kdcs;
                if (realmKdcs != null) {
                    KerberosRealms.declare(KerberosRealms.realm(String.valueOf(user)), KerberosRealms.kdcs(realmKdcs));
                }
            }
        }
        if (protocol.endsWith("SSL")) {
            put(client, SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststore);
            putSecret(client, SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword);
            if (truststore != null) {
                client.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, truststoreType);
            }
            put(client, SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keystore);
            putSecret(client, SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, keystorePassword);
            putSecret(client, SslConfigs.SSL_KEY_PASSWORD_CONFIG, keyPassword);
            client.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, verifyHostname ? "https" : "");
        }
        return client;
    }

    /**
     * Writes the JAAS entry of the sign-in, quoting every value.
     *
     * @return the entry
     */
    String jaas()
    {
        String name = String.valueOf(user);
        if (kerberos()) {
            String file = keytab;
            if (file != null) {
                return "com.sun.security.auth.module.Krb5LoginModule required useKeyTab=true storeKey=true useTicketCache=false keyTab="
                        + quote(file) + " principal=" + quote(name) + ";";
            }
            return "com.sun.security.auth.module.Krb5LoginModule required useKeyTab=false storeKey=true useTicketCache=false principal="
                    + quote(name) + " " + PASSWORD_OPTION + "=" + quote(String.valueOf(password)) + ";";
        }
        String module = "PLAIN".equals(mechanism) ? "org.apache.kafka.common.security.plain.PlainLoginModule"
                : "org.apache.kafka.common.security.scram.ScramLoginModule";
        return module + " required username=" + quote(name) + " password=" + quote(String.valueOf(password)) + ";";
    }

    /**
     * Quotes a JAAS option value: inside double quotes, backslashes and double quotes are escaped.
     *
     * @param value the value
     * @return it, quoted
     */
    static String quote(String value)
    {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void put(Map<String, Object> client, String key, @Nullable String value)
    {
        if (value != null) {
            client.put(key, value);
        }
    }

    private static void putSecret(Map<String, Object> client, String key, @Nullable String value)
    {
        if (value != null) {
            client.put(key, new Password(value));
        }
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

    private static String upper(ServiceConfig config, String field, String fallback)
    {
        return textOr(config, field, fallback).toUpperCase(Locale.ROOT);
    }
}
