// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientException;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.sync.Settings;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The agent's settings, named {@code grantforge.*} in its properties file: the GrantForge server and token, the MongoDB
 * deployment and how the agent signs in to it, the roles it keeps and the databases it looks after, how often it brings
 * MongoDB in step, and how it reads accesses.
 *
 * @param agent the signed-policy agent's settings
 * @param connection the deployment and the agent's sign-in
 * @param rolePrefix the start of the names of the roles the agent keeps, one per user
 * @param unmanaged the users the agent leaves alone, besides itself
 * @param databases the databases whose collections the agent grants by name, or none for every database
 * @param syncInterval how often the agent brings MongoDB in step
 * @param foldWindow how long identical allowed accesses are folded into one event; zero sends each
 * @param audit how the agent reads accesses, or {@code null} when it does not
 */
record AgentConfig(AgentSettings agent, Connection connection, String rolePrefix, Set<String> unmanaged, Set<String> databases, Duration syncInterval,
        Duration foldWindow, @Nullable Audit audit)
{
    /** The prefix of the settings. */
    static final String PREFIX = Settings.PREFIX;

    static final String SCRAM_SHA_256 = "SCRAM-SHA-256";
    static final String SCRAM_SHA_1 = "SCRAM-SHA-1";
    static final String X509 = "MONGODB-X509";
    static final String PLAIN = "PLAIN";
    static final String GSSAPI = "GSSAPI";
    /** How the agent can sign in, as MongoDB names the mechanisms. */
    static final List<String> MECHANISMS = List.of(SCRAM_SHA_256, SCRAM_SHA_1, X509, PLAIN, GSSAPI);
    /** The database of users MongoDB does not keep passwords for. */
    static final String EXTERNAL = "$external";

    /** What role prefixes may hold, so that each role name stays a plain name. */
    private static final Pattern ROLE_PREFIX = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,30}_");

    /** Copies the names. */
    AgentConfig
    {
        unmanaged = Set.copyOf(unmanaged);
        databases = Set.copyOf(databases);
    }

    /**
     * The deployment and how the agent signs in.
     *
     * @param uri the connection string, without credentials
     * @param user the agent's user; with a client certificate its subject, or {@code null} to take it from the certificate
     * @param password its password, or {@code null} without one
     * @param mechanism how it signs in, one of {@link #MECHANISMS}
     * @param source the database the user is defined in, for SCRAM
     * @param caFile the PEM file of the CAs that verify the servers, or {@code null} for Java's
     * @param certificateKeyFile the PEM file of the client certificate and its key, or {@code null} without one
     * @param certificateKeyPassword the password of that key, or {@code null}
     * @param keytab the Kerberos keytab, or {@code null} to use the password
     * @param serviceName the service name of MongoDB's Kerberos principal
     * @param timeout how long finding a server, connecting and a command may take
     */
    record Connection(String uri, @Nullable String user, @Nullable String password, String mechanism, String source, @Nullable String caFile,
            @Nullable String certificateKeyFile, @Nullable String certificateKeyPassword, @Nullable String keytab, String serviceName, Duration timeout)
    {
        @Override
        public String toString()
        {
            return "Connection[uri=" + uri + ", user=" + user + ", mechanism=" + mechanism + "]";
        }
    }

    /**
     * How the agent reads accesses.
     *
     * @param files the audit log files of MongoDB Enterprise or Percona Server, or none to read the profiler
     * @param delay how far behind the present the profiler is read, so that entries of the same moment are all there
     * @param interval how often it reads
     * @param batch the most entries one read takes of a file or database
     */
    record Audit(List<Path> files, Duration delay, Duration interval, int batch)
    {
        /** Copies the files. */
        Audit
        {
            files = List.copyOf(files);
        }
    }

    /**
     * Reads the settings.
     *
     * @param values the {@code grantforge.*} settings
     * @param version the agent's version
     * @return the settings
     * @throws IOException when a file the settings name cannot be read
     * @throws IllegalArgumentException when a setting is missing or invalid
     */
    static AgentConfig parse(Map<String, String> values, String version) throws IOException
    {
        Settings settings = new Settings(values, "MongoDB");
        AgentSettings agent = settings.agent(version);
        String prefix = settings.rolePrefix("mongodb.role.prefix", ROLE_PREFIX.asMatchPredicate(),
                "start with a letter, hold letters, digits and underscores, end with an underscore and be at most 32 characters, such as"
                        + " grantforge_");
        Audit audit = null;
        if (settings.flag("audit.log", true)) {
            audit = new Audit(settings.names("mongodb.audit.files").stream().sorted().map(Path::of).toList(),
                    settings.millis("audit.log.delay.ms", 2000, 0, 3_600_000), settings.millis("audit.log.interval.ms", 5000, 1000, 600_000),
                    (int) settings.number("audit.log.batch", 1000, 1, 100_000));
        }
        return new AgentConfig(agent, connection(settings), prefix, settings.names("mongodb.unmanaged.users"), settings.names("mongodb.databases"),
                settings.syncInterval(), settings.foldWindow(), audit);
    }

    private static Connection connection(Settings settings) throws IOException
    {
        String uri = settings.required("mongodb.uri");
        ConnectionString parsed;
        try {
            parsed = new ConnectionString(uri);
        }
        catch (IllegalArgumentException | MongoClientException wrong) {
            // The driver's message would repeat a password the string may hold, so only the form is named.
            throw new IllegalArgumentException(PREFIX + "mongodb.uri is a connection string such as mongodb://host:port,host:port/?replicaSet=rs0", wrong);
        }
        if (parsed.getUsername() != null || parsed.getPassword() != null) {
            throw new IllegalArgumentException(PREFIX + "mongodb.uri holds no credentials; use " + PREFIX + "mongodb.user and " + PREFIX + "mongodb.password.file");
        }
        String mechanism = settings.text("mongodb.auth.mechanism", SCRAM_SHA_256).toUpperCase(Locale.ROOT);
        if (!MECHANISMS.contains(mechanism)) {
            throw new IllegalArgumentException(PREFIX + "mongodb.auth.mechanism is SCRAM-SHA-256, SCRAM-SHA-1, MONGODB-X509, PLAIN or GSSAPI, not " + mechanism);
        }
        String user = settings.optional("mongodb.user");
        String passwordFile = settings.optional("mongodb.password.file");
        String password = passwordFile == null ? null : Settings.read(passwordFile, StandardCharsets.UTF_8).trim();
        String keytab = settings.optional("mongodb.kerberos.keytab");
        String certificateKey = settings.optional("mongodb.tls.certificate.key.file");
        String keyPasswordFile = settings.optional("mongodb.tls.certificate.key.password.file");
        String ca = settings.optional("mongodb.tls.ca.file");
        check(mechanism, user, password, keytab, certificateKey);
        if ((ca != null || certificateKey != null) && !Boolean.TRUE.equals(parsed.getSslEnabled())) {
            throw new IllegalArgumentException(PREFIX + "mongodb.tls.* files need tls=true in " + PREFIX + "mongodb.uri");
        }
        return new Connection(uri, user, password, mechanism, settings.text("mongodb.auth.source", "admin"), ca, certificateKey,
                keyPasswordFile == null ? null : Settings.read(keyPasswordFile, StandardCharsets.UTF_8).trim(), keytab,
                settings.text("mongodb.kerberos.service.name", "mongodb"), settings.millis("mongodb.timeout.ms", 30000, 1, 600_000));
    }

    private static void check(String mechanism, @Nullable String user, @Nullable String password, @Nullable String keytab, @Nullable String certificateKey)
    {
        if (X509.equals(mechanism)) {
            if (certificateKey == null) {
                throw new IllegalArgumentException(PREFIX + "mongodb.tls.certificate.key.file is the client certificate MONGODB-X509 signs in with");
            }
            return;
        }
        if (user == null) {
            throw new IllegalArgumentException(PREFIX + "mongodb.user names the agent's user");
        }
        if (GSSAPI.equals(mechanism) ? keytab == null && password == null : password == null) {
            throw new IllegalArgumentException(PREFIX + "mongodb.password.file" + (GSSAPI.equals(mechanism) ? " or " + PREFIX + "mongodb.kerberos.keytab" : "")
                    + " signs the agent's user in with " + mechanism);
        }
    }
}
