// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.SigningKey;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The agent's settings, named {@code grantforge.*} in its properties file: the GrantForge server and token, the
 * PostgreSQL servers and how the agent signs in to them, the databases and roles it keeps, how often it brings
 * PostgreSQL in step, and how it reads the server log.
 *
 * @param agent the signed-policy agent's settings
 * @param connection the PostgreSQL servers and the agent's sign-in
 * @param rolePrefix the start of the names of the roles the agent keeps, one per user
 * @param unmanaged the users the agent leaves alone, besides itself
 * @param databases the databases the agent keeps, or none for every database that allows connections
 * @param syncInterval how often the agent brings PostgreSQL in step
 * @param foldWindow how long identical allowed accesses are folded into one event; zero sends each
 * @param serverLog how the agent reads the server log, or {@code null} when it does not
 */
record AgentConfig(AgentSettings agent, Connection connection, String rolePrefix, Set<String> unmanaged, Set<String> databases,
        Duration syncInterval, Duration foldWindow, @Nullable ServerLog serverLog)
{
    /** The prefix of the settings. */
    static final String PREFIX = "grantforge.";

    /** libpq's TLS modes, from none to a verified server certificate for the host's name. */
    static final List<String> SSL_MODES = List.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full");

    /**
     * What role prefixes may hold, so that each role name stays readable in PostgreSQL's own statements and leaves room
     * for the user's name within PostgreSQL's 63 bytes.
     */
    private static final Pattern ROLE_PREFIX = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,30}_");
    /** {@code host}, {@code host:port}, {@code [IPv6]} or {@code [IPv6]:port}. */
    private static final Pattern HOST = Pattern.compile("(\\[[0-9A-Fa-f:.]+]|[A-Za-z0-9](?:[A-Za-z0-9.-]{0,252}[A-Za-z0-9])?)(?::(\\d{1,5}))?");

    /** Copies the users and databases. */
    AgentConfig
    {
        unmanaged = Set.copyOf(unmanaged);
        databases = Set.copyOf(databases);
    }

    /**
     * The PostgreSQL servers and how the agent signs in.
     *
     * @param hosts the servers, as {@code host:port}, in the order they are tried
     * @param database the database the agent signs in to first, to read the databases, roles and server log
     * @param user the agent's PostgreSQL user
     * @param password its password, or {@code null} when it signs in with a certificate or without a password
     * @param sslMode how TLS is used, as libpq's {@code sslmode} names it
     * @param ca the PEM CA certificate that verifies the servers, or {@code null} for Java's
     * @param certificate the PEM client certificate it signs in with, or {@code null}
     * @param key the client certificate's PKCS #8 private key, or {@code null}
     * @param timeout how long connecting and a statement may take
     */
    record Connection(List<String> hosts, String database, String user, @Nullable String password, String sslMode, @Nullable String ca,
            @Nullable String certificate, @Nullable String key, Duration timeout)
    {
        /** Copies the servers. */
        Connection
        {
            hosts = List.copyOf(hosts);
        }

        @Override
        public String toString()
        {
            return "Connection[hosts=" + hosts + ", database=" + database + ", user=" + user + ", certificate=" + certificate + "]";
        }
    }

    /**
     * How the agent reads the server log.
     *
     * @param interval how often it reads
     * @param maxBytes the most bytes one read takes, so that a backlog is read over several turns
     */
    record ServerLog(Duration interval, long maxBytes)
    {
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
        String trimmed = version.trim();
        if (trimmed.isEmpty() || version.length() > 64) {
            throw new IllegalArgumentException("the agent version must be 1 to 64 characters");
        }
        int batch = (int) number(values, "audit.batch.size", 500, 1, AgentSettings.MAX_BATCH);
        int queue = (int) number(values, "audit.queue.capacity", 10000, 1, 1_000_000);
        if (queue < batch) {
            throw new IllegalArgumentException(PREFIX + "audit.queue.capacity must be at least " + PREFIX + "audit.batch.size");
        }
        AgentSettings.Builder agent = AgentSettings.builder().server(URI.create(required(values, "server.url")))
                .token(read(required(values, "token.file"), StandardCharsets.UTF_8).trim()).instance(required(values, "instance"))
                .cacheDirectory(Paths.get(required(values, "cache.dir"))).agentVersion(version)
                .timeouts(Duration.ofMillis(number(values, "connect.timeout.ms", 5000, 1, Integer.MAX_VALUE)),
                        Duration.ofMillis(number(values, "read.timeout.ms", 8000, 1, Integer.MAX_VALUE)))
                .refreshInterval(Duration.ofMillis(number(values, "refresh.interval.ms", 30000, 1000, Long.MAX_VALUE)))
                .audit(batch, Duration.ofMillis(number(values, "audit.flush.interval.ms", 5000, 1, Integer.MAX_VALUE)), queue)
                .spoolLimitBytes(number(values, "audit.spool.limit.bytes", 64L * 1024 * 1024, 0, Long.MAX_VALUE));
        String key = optional(values, "signing.key.file");
        if (key != null) {
            agent.trustedKey(SigningKey.of(read(key, StandardCharsets.US_ASCII)));
        }

        String prefix = values.getOrDefault(PREFIX + "postgresql.role.prefix", "grantforge_").trim();
        if (!ROLE_PREFIX.matcher(prefix).matches() || prefix.toLowerCase(Locale.ROOT).startsWith("pg_")) {
            throw new IllegalArgumentException(PREFIX + "postgresql.role.prefix must start with a letter, hold letters, digits and underscores,"
                    + " end with an underscore, be at most 32 characters and not start with pg_, such as grantforge_");
        }
        ServerLog log = null;
        if (Boolean.parseBoolean(values.getOrDefault(PREFIX + "audit.server.log", "true").trim())) {
            log = new ServerLog(Duration.ofMillis(number(values, "audit.server.log.interval.ms", 5000, 1000, 600_000)),
                    number(values, "audit.server.log.max.bytes", 16L * 1024 * 1024, 64 * 1024, 1024L * 1024 * 1024));
        }
        return new AgentConfig(agent.build(), connection(values), prefix, names(values, "postgresql.unmanaged.users"),
                names(values, "postgresql.databases"), Duration.ofMillis(number(values, "sync.interval.ms", 10000, 1000, 3_600_000)),
                Duration.ofMillis(number(values, "audit.fold.window.ms", 10000, 0, 3_600_000)), log);
    }

    private static Set<String> names(Map<String, String> values, String key)
    {
        Set<String> names = new LinkedHashSet<>();
        String listed = optional(values, key);
        if (listed != null) {
            for (String name : listed.split(",", -1)) {
                if (!name.isBlank()) {
                    names.add(name.strip());
                }
            }
        }
        return names;
    }

    private static Connection connection(Map<String, String> values) throws IOException
    {
        List<String> hosts = new ArrayList<>();
        for (String entry : required(values, "postgresql.hosts").split(",", -1)) {
            String host = host(entry.strip());
            if (!hosts.contains(host)) {
                hosts.add(host);
            }
        }
        String mode = values.getOrDefault(PREFIX + "postgresql.ssl.mode", "prefer").trim().toLowerCase(Locale.ROOT);
        if (!SSL_MODES.contains(mode)) {
            throw new IllegalArgumentException(PREFIX + "postgresql.ssl.mode is one of " + String.join(", ", SSL_MODES));
        }
        String certificate = optional(values, "postgresql.tls.cert");
        String key = optional(values, "postgresql.tls.key");
        if (certificate == null != (key == null)) {
            throw new IllegalArgumentException(PREFIX + "postgresql.tls.cert and " + PREFIX + "postgresql.tls.key go together");
        }
        String passwordFile = optional(values, "postgresql.password.file");
        if (certificate != null && passwordFile != null) {
            throw new IllegalArgumentException("the agent signs in with " + PREFIX + "postgresql.tls.cert or " + PREFIX
                    + "postgresql.password.file, not both");
        }
        boolean verifies = mode.startsWith("verify-");
        if (certificate != null && SSL_MODES.indexOf(mode) < SSL_MODES.indexOf("require")) {
            throw new IllegalArgumentException(PREFIX + "postgresql.tls.cert needs " + PREFIX + "postgresql.ssl.mode require, verify-ca or verify-full");
        }
        String ca = optional(values, "postgresql.tls.ca");
        if (ca != null && !verifies) {
            throw new IllegalArgumentException(PREFIX + "postgresql.tls.ca needs " + PREFIX + "postgresql.ssl.mode verify-ca or verify-full");
        }
        if (certificate != null && verifies && ca == null) {
            throw new IllegalArgumentException(PREFIX + "postgresql.tls.cert with " + mode + " needs " + PREFIX + "postgresql.tls.ca");
        }
        return new Connection(hosts, values.getOrDefault(PREFIX + "postgresql.database", "postgres").trim(), required(values, "postgresql.user"),
                passwordFile == null ? null : read(passwordFile, StandardCharsets.UTF_8).trim(), mode, ca, certificate, key,
                Duration.ofMillis(number(values, "postgresql.timeout.ms", 30000, 1, 600_000)));
    }

    /** A host with an optional port from 1 to 65535, as {@code host:port}. */
    private static String host(String entry)
    {
        Matcher matcher = HOST.matcher(entry);
        int port = matcher.matches() && matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 5432;
        if (!matcher.matches() || port < 1 || port > 65_535) {
            throw new IllegalArgumentException(PREFIX + "postgresql.hosts lists servers as host or host:port such as pg1:5432, not " + entry);
        }
        return matcher.group(1).toLowerCase(Locale.ROOT) + ":" + port;
    }

    private static String read(String path, Charset charset) throws IOException
    {
        return new String(Files.readAllBytes(Paths.get(path)), charset);
    }

    private static String required(Map<String, String> values, String key)
    {
        String value = optional(values, key);
        if (value == null) {
            throw new IllegalArgumentException("missing PostgreSQL agent setting " + PREFIX + key);
        }
        return value;
    }

    private static @Nullable String optional(Map<String, String> values, String key)
    {
        String configured = values.get(PREFIX + key);
        String value = configured == null ? "" : configured.trim();
        return value.isEmpty() ? null : value;
    }

    private static long number(Map<String, String> values, String key, long fallback, long minimum, long maximum)
    {
        String configured = optional(values, key);
        long value;
        try {
            value = configured == null ? fallback : Long.parseLong(configured);
        }
        catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(PREFIX + key + " must be an integer", invalid);
        }
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(PREFIX + key + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }
}
