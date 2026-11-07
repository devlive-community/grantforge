// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientException;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The settings of a MongoDB service, checked. The deployment is named by a connection string as every MongoDB client
 * takes it, holding its hosts and options such as {@code replicaSet} and {@code tls}, but no credentials, which are
 * settings of their own so that the password stays encrypted. The client signs in with SCRAM, a client certificate,
 * LDAP or Kerberos, and takes its TLS files as {@code mongosh} does: a CA file, and one file holding a certificate and
 * its key.
 *
 * @param uri the connection string
 * @param user the user name; for a client certificate its subject, or {@code null} to take it from the certificate
 * @param password the password, or {@code null} without one
 * @param mechanism how the client signs in, one of {@link #MECHANISMS}
 * @param source the database the user is defined in, for SCRAM
 * @param caFile the PEM file of the CAs that verify the servers, or {@code null} for the JVM's
 * @param certificateKeyFile the PEM file of the client certificate and its key, or {@code null} without one
 * @param certificateKeyPassword the password of that key, or {@code null}
 * @param keytab the Kerberos keytab on the server, or {@code null} to use the password
 * @param kdcs the realm's KDCs, or {@code null} for the server's krb5.conf
 * @param serviceName the service name of MongoDB's Kerberos principal
 * @param timeout how long finding a server, connecting and a command may take, in milliseconds
 */
record MongoDbSettings(String uri, @Nullable String user, @Nullable String password, String mechanism, String source, @Nullable String caFile,
        @Nullable String certificateKeyFile, @Nullable String certificateKeyPassword, @Nullable String keytab, @Nullable String kdcs, String serviceName,
        int timeout)
{
    static final String URI = "mongodb.uri";
    static final String USER = "username";
    static final String PASSWORD = "password";
    static final String MECHANISM = "auth.mechanism";
    static final String SOURCE = "auth.source";
    static final String CA_FILE = "tls.ca.file";
    static final String CERTIFICATE_KEY_FILE = "tls.certificate.key.file";
    static final String CERTIFICATE_KEY_PASSWORD = "tls.certificate.key.password";
    static final String KEYTAB = "kerberos.keytab";
    static final String KDC = "kerberos.kdc";
    static final String SERVICE_NAME = "kerberos.service.name";
    static final String TIMEOUT = "timeout";

    static final String SCRAM_SHA_256 = "SCRAM-SHA-256";
    static final String SCRAM_SHA_1 = "SCRAM-SHA-1";
    static final String X509 = "MONGODB-X509";
    static final String PLAIN = "PLAIN";
    static final String GSSAPI = "GSSAPI";
    /** How a client can sign in, as MongoDB names the mechanisms. */
    static final List<String> MECHANISMS = List.of(SCRAM_SHA_256, SCRAM_SHA_1, X509, PLAIN, GSSAPI);
    static final String DEFAULT_SOURCE = "admin";
    /** The database of users MongoDB does not keep passwords for. */
    static final String EXTERNAL = "$external";
    static final String DEFAULT_SERVICE_NAME = "mongodb";
    static final int DEFAULT_TIMEOUT = 30_000;

    /**
     * Checks a service's settings.
     *
     * @param config the settings
     * @return what is wrong, by field; empty when they are usable
     */
    static List<ConfigProblem> problems(ServiceConfig config)
    {
        List<ConfigProblem> problems = new ArrayList<>();
        ConnectionString uri = uri(config, problems);
        String mechanism = mechanism(config);
        String user = text(config, USER);
        String password = config.get(PASSWORD);
        boolean hasPassword = password != null && !password.isEmpty();
        if (!MECHANISMS.contains(mechanism)) {
            problems.add(invalid(MECHANISM, "the mechanism is SCRAM-SHA-256, SCRAM-SHA-1, MONGODB-X509, PLAIN or GSSAPI"));
        }
        else if (X509.equals(mechanism)) {
            if (text(config, CERTIFICATE_KEY_FILE) == null) {
                problems.add(new ConfigProblem(CERTIFICATE_KEY_FILE, ConfigProblem.Reason.REQUIRED, "MONGODB-X509 signs in with a client certificate"));
            }
        }
        else {
            if (user == null) {
                problems.add(new ConfigProblem(USER, ConfigProblem.Reason.REQUIRED, GSSAPI.equals(mechanism) ? "name the Kerberos principal"
                        : "name the user that looks up databases and collections"));
            }
            if (!hasPassword && !(GSSAPI.equals(mechanism) && text(config, KEYTAB) != null)) {
                problems.add(new ConfigProblem(PASSWORD, ConfigProblem.Reason.REQUIRED, GSSAPI.equals(mechanism) ? "give the principal's password or a keytab"
                        : "give the user's password"));
            }
        }
        if (!GSSAPI.equals(mechanism) && text(config, KEYTAB) != null) {
            problems.add(invalid(KEYTAB, "a keytab signs in with GSSAPI only"));
        }
        if (uri != null && !Boolean.TRUE.equals(uri.getSslEnabled())) {
            for (String field : List.of(CA_FILE, CERTIFICATE_KEY_FILE)) {
                if (text(config, field) != null) {
                    problems.add(invalid(field, "TLS files need tls=true in the connection string"));
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

    /** Parses the connection string, adding what is wrong with it. */
    private static @Nullable ConnectionString uri(ServiceConfig config, List<ConfigProblem> problems)
    {
        String value = text(config, URI);
        if (value == null) {
            problems.add(new ConfigProblem(URI, ConfigProblem.Reason.REQUIRED,
                    "give the connection string, such as mongodb://db1.example.com:27017,db2.example.com:27017/?replicaSet=rs0"));
            return null;
        }
        ConnectionString parsed;
        try {
            parsed = new ConnectionString(value);
        }
        catch (IllegalArgumentException | MongoClientException wrong) {
            // The driver's message would repeat a password the string may hold, so only the form is named.
            problems.add(invalid(URI, "this is no connection string; write it as mongodb://host:port,host:port/?option=value or mongodb+srv://host/"));
            return null;
        }
        if (parsed.getUsername() != null || parsed.getPassword() != null) {
            problems.add(invalid(URI, "the connection string holds no credentials; give them as the username and password, where the password stays"
                    + " encrypted"));
            return null;
        }
        return parsed;
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
    static MongoDbSettings of(ServiceConfig config)
    {
        List<ConfigProblem> problems = problems(config);
        if (!problems.isEmpty()) {
            ConfigProblem first = problems.get(0);
            throw new IllegalArgumentException(first.field() + ": " + first.detail());
        }
        String source = text(config, SOURCE);
        String serviceName = text(config, SERVICE_NAME);
        String password = config.get(PASSWORD);
        return new MongoDbSettings(String.valueOf(text(config, URI)), text(config, USER), password == null || password.isEmpty() ? null : password,
                mechanism(config), source == null ? DEFAULT_SOURCE : source, text(config, CA_FILE), text(config, CERTIFICATE_KEY_FILE),
                config.get(CERTIFICATE_KEY_PASSWORD), text(config, KEYTAB), text(config, KDC), serviceName == null ? DEFAULT_SERVICE_NAME : serviceName,
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

    private static String mechanism(ServiceConfig config)
    {
        String value = text(config, MECHANISM);
        return value == null ? SCRAM_SHA_256 : value.toUpperCase(Locale.ROOT);
    }

    private static @Nullable String text(ServiceConfig config, String field)
    {
        String value = config.get(field);
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Override
    public String toString()
    {
        return "MongoDbSettings[uri=" + uri + ", user=" + user + ", mechanism=" + mechanism + "]";
    }
}
