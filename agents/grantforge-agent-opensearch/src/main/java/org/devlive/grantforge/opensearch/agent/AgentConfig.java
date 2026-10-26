// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

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
 * OpenSearch nodes and how the agent signs in to them, the roles it keeps and the users it leaves alone, how often it
 * brings OpenSearch in step, and how it reads the security plugin's audit log.
 *
 * @param agent the signed-policy agent's settings
 * @param connection the OpenSearch nodes and the agent's sign-in
 * @param rolePrefix the start of the names of the roles the agent keeps, one per user
 * @param unmanaged the users the agent leaves alone, besides itself
 * @param syncInterval how often the agent brings OpenSearch in step
 * @param foldWindow how long identical allowed accesses are folded into one event; zero sends each
 * @param auditLog how the agent reads the audit log, or {@code null} when it does not
 */
record AgentConfig(AgentSettings agent, Connection connection, String rolePrefix, Set<String> unmanaged, Duration syncInterval, Duration foldWindow,
        @Nullable AuditLogSettings auditLog)
{
    /** The prefix of the settings. */
    static final String PREFIX = "grantforge.";

    /** How a node's certificate is checked: its chain and name, its chain only, or not at all. */
    static final List<String> VERIFICATION_MODES = List.of("full", "certificate", "none");

    /** What role prefixes may hold, so that each role name stays readable in OpenSearch's own tools. */
    private static final Pattern ROLE_PREFIX = Pattern.compile("[a-z][a-z0-9_-]{0,30}_");
    /** {@code [scheme://]host[:port]} with a host name, an IPv4 address or an {@code [IPv6]} address, and nothing after. */
    private static final Pattern HOST = Pattern.compile(
            "(?:([A-Za-z][A-Za-z0-9+.-]*)://)?(\\[[0-9A-Fa-f:.]+]|[A-Za-z0-9](?:[A-Za-z0-9.-]{0,252}[A-Za-z0-9])?)(?::(\\d{1,5}))?/?");

    /** Copies the users. */
    AgentConfig
    {
        unmanaged = Set.copyOf(unmanaged);
    }

    /**
     * The OpenSearch nodes and how the agent signs in.
     *
     * @param hosts the nodes, as {@code scheme://host:port}, in the order they are tried
     * @param user the agent's OpenSearch user, or {@code null} when it signs in with a certificate
     * @param password its password, or {@code null}
     * @param verificationMode full, certificate or none
     * @param ca the PEM CA certificates that verify the nodes, or {@code null} for Java's
     * @param certificate the PEM client certificate it signs in with, or {@code null}
     * @param key the client certificate's PEM private key, or {@code null}
     * @param keyPassphrase the passphrase of an encrypted key, or {@code null}
     * @param timeout how long connecting and a request may take
     */
    record Connection(List<String> hosts, @Nullable String user, @Nullable String password, String verificationMode, @Nullable String ca,
            @Nullable String certificate, @Nullable String key, @Nullable String keyPassphrase, Duration timeout)
    {
        /** Copies the nodes. */
        Connection
        {
            hosts = List.copyOf(hosts);
        }

        @Override
        public String toString()
        {
            return "Connection[hosts=" + hosts + ", user=" + user + ", verificationMode=" + verificationMode + ", certificate=" + certificate + "]";
        }
    }

    /**
     * How the agent reads the audit log.
     *
     * @param index the indices the security plugin writes the audit log to
     * @param interval how often it reads
     * @param maxDocuments the most audit documents one read takes, so that a backlog is read over several turns
     */
    record AuditLogSettings(String index, Duration interval, int maxDocuments)
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

        String prefix = values.getOrDefault(PREFIX + "opensearch.role.prefix", "grantforge_").trim();
        if (!ROLE_PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException(PREFIX + "opensearch.role.prefix must start with a lowercase letter, hold lowercase letters, digits,"
                    + " hyphens and underscores, end with an underscore and be at most 32 characters, such as grantforge_");
        }
        AuditLogSettings log = null;
        if (Boolean.parseBoolean(values.getOrDefault(PREFIX + "audit.log", "true").trim())) {
            String index = values.getOrDefault(PREFIX + "audit.log.index", "security-auditlog-*").trim();
            if (index.isEmpty() || index.contains(",") || index.contains("/") || index.startsWith("-")) {
                throw new IllegalArgumentException(PREFIX + "audit.log.index names the audit log's indices as one name or pattern, such as"
                        + " security-auditlog-*");
            }
            log = new AuditLogSettings(index, Duration.ofMillis(number(values, "audit.log.interval.ms", 5000, 1000, 600_000)),
                    (int) number(values, "audit.log.max.documents", 1000, 10, 10_000));
        }
        return new AgentConfig(agent.build(), connection(values), prefix, names(values, "opensearch.unmanaged.users"),
                Duration.ofMillis(number(values, "sync.interval.ms", 10000, 1000, 3_600_000)),
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
        for (String entry : required(values, "opensearch.hosts").split(",", -1)) {
            String host = host(entry.strip());
            if (!hosts.contains(host)) {
                hosts.add(host);
            }
        }
        boolean secure = hosts.get(0).startsWith("https:");
        if (hosts.stream().anyMatch(host -> host.startsWith("https:") != secure)) {
            throw new IllegalArgumentException(PREFIX + "opensearch.hosts lists the nodes of one cluster, all http or all https");
        }
        String mode = values.getOrDefault(PREFIX + "opensearch.ssl.verification.mode", "full").trim().toLowerCase(Locale.ROOT);
        if (!VERIFICATION_MODES.contains(mode)) {
            throw new IllegalArgumentException(PREFIX + "opensearch.ssl.verification.mode is full, certificate or none");
        }
        String ca = optional(values, "opensearch.tls.ca");
        String certificate = optional(values, "opensearch.tls.cert");
        String key = optional(values, "opensearch.tls.key");
        if (certificate == null != (key == null)) {
            throw new IllegalArgumentException(PREFIX + "opensearch.tls.cert and " + PREFIX + "opensearch.tls.key go together");
        }
        if (!secure && (ca != null || certificate != null)) {
            throw new IllegalArgumentException("the agent's TLS settings are for https:// nodes");
        }
        if (ca != null && "none".equals(mode)) {
            throw new IllegalArgumentException(PREFIX + "opensearch.tls.ca checks the nodes; the verification mode none checks nothing");
        }
        String user = optional(values, "opensearch.user");
        String passwordFile = optional(values, "opensearch.password.file");
        if (certificate == null) {
            if (user == null || passwordFile == null) {
                throw new IllegalArgumentException("the agent signs in with " + PREFIX + "opensearch.user and " + PREFIX + "opensearch.password.file, or"
                        + " with " + PREFIX + "opensearch.tls.cert and " + PREFIX + "opensearch.tls.key");
            }
        }
        else if (user != null || passwordFile != null) {
            throw new IllegalArgumentException("the agent signs in with " + PREFIX + "opensearch.tls.cert or " + PREFIX + "opensearch.user, not both");
        }
        String passphraseFile = optional(values, "opensearch.tls.key.passphrase.file");
        if (passphraseFile != null && key == null) {
            throw new IllegalArgumentException(PREFIX + "opensearch.tls.key.passphrase.file is the passphrase of " + PREFIX + "opensearch.tls.key");
        }
        return new Connection(hosts, user, passwordFile == null ? null : read(passwordFile, StandardCharsets.UTF_8).trim(), mode, ca, certificate,
                key, passphraseFile == null ? null : read(passphraseFile, StandardCharsets.UTF_8).trim(),
                Duration.ofMillis(number(values, "opensearch.timeout.ms", 30000, 1, 600_000)));
    }

    /** A node as {@code scheme://host:port}, https and 9200 unless said otherwise. */
    private static String host(String entry)
    {
        Matcher matcher = HOST.matcher(entry);
        boolean matches = matcher.matches();
        String scheme = matches && matcher.group(1) != null ? matcher.group(1).toLowerCase(Locale.ROOT) : "https";
        int port = matches && matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 9200;
        if (!matches || !"http".equals(scheme) && !"https".equals(scheme) || port < 1 || port > 65_535) {
            throw new IllegalArgumentException(PREFIX + "opensearch.hosts lists nodes as URLs such as https://os1:9200, not " + entry);
        }
        return scheme + "://" + matcher.group(2).toLowerCase(Locale.ROOT) + ":" + port;
    }

    private static String read(String path, Charset charset) throws IOException
    {
        return new String(Files.readAllBytes(Paths.get(path)), charset);
    }

    private static String required(Map<String, String> values, String key)
    {
        String value = optional(values, key);
        if (value == null) {
            throw new IllegalArgumentException("missing OpenSearch agent setting " + PREFIX + key);
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
