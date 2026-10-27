// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.sync.Settings;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The agent's settings, named {@code grantforge.*} in its properties file: the GrantForge server and token, the
 * Elasticsearch nodes and how the agent signs in to them, the roles it keeps and the users it leaves alone, how often it
 * brings Elasticsearch in step, and which audit log files of the nodes it reads.
 *
 * @param agent the signed-policy agent's settings
 * @param connection the Elasticsearch nodes and the agent's sign-in
 * @param rolePrefix the start of the names of the roles the agent keeps, one per user
 * @param unmanaged the users the agent leaves alone, besides itself
 * @param syncInterval how often the agent brings Elasticsearch in step
 * @param foldWindow how long identical allowed accesses are folded into one event; zero sends each
 * @param auditLog the audit log files the agent reads, or {@code null} when it reads none
 */
record AgentConfig(AgentSettings agent, Connection connection, String rolePrefix, Set<String> unmanaged, Duration syncInterval, Duration foldWindow,
        @Nullable AuditFiles auditLog)
{
    /** The prefix of the settings. */
    static final String PREFIX = Settings.PREFIX;

    /** How a node's certificate is checked: its chain and name, its chain only, or not at all. */
    static final List<String> VERIFICATION_MODES = List.of("full", "certificate", "none");

    /** What role prefixes may hold, so that each role name stays readable in Elasticsearch's own tools. */
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
     * The Elasticsearch nodes and how the agent signs in.
     *
     * @param hosts the nodes, as {@code scheme://host:port}, in the order they are tried
     * @param user the agent's Elasticsearch user, or {@code null} when it signs in otherwise
     * @param password its password, or {@code null}
     * @param apiKey the encoded API key the agent signs in with, or {@code null}
     * @param verificationMode full, certificate or none
     * @param ca the PEM CA certificates that verify the nodes, or {@code null} for Java's
     * @param certificate the PEM client certificate it signs in with, or {@code null}
     * @param key the client certificate's PEM private key, or {@code null}
     * @param keyPassphrase the passphrase of an encrypted key, or {@code null}
     * @param timeout how long connecting and a request may take
     */
    record Connection(List<String> hosts, @Nullable String user, @Nullable String password, @Nullable String apiKey, String verificationMode,
            @Nullable String ca, @Nullable String certificate, @Nullable String key, @Nullable String keyPassphrase, Duration timeout)
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
     * The audit log files the agent reads. Elasticsearch writes its audit log to a file of each node only, so the agent
     * reads the files it can see: those of the node it runs on, or those a shared directory collects.
     *
     * @param files the files, such as {@code /var/log/elasticsearch/production_audit.json}
     * @param interval how often it reads
     * @param maxLines the most lines one read takes of each file, so that a backlog is read over several turns
     */
    record AuditFiles(List<Path> files, Duration interval, int maxLines)
    {
        /** Copies the files. */
        AuditFiles
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
        Settings settings = new Settings(values, "Elasticsearch");
        AgentSettings agent = settings.agent(version);
        String prefix = settings.rolePrefix("elasticsearch.role.prefix",
                ROLE_PREFIX.asMatchPredicate(),
                "start with a lowercase letter, hold lowercase letters, digits, hyphens and underscores, end with an"
                        + " underscore and be at most 32 characters, such as grantforge_");
        AuditFiles log = null;
        Set<String> files = settings.names("audit.log.files");
        if (!files.isEmpty()) {
            log = new AuditFiles(files.stream().map(Paths::get).toList(), settings.millis("audit.log.interval.ms", 5000, 1000, 600_000),
                    (int) settings.number("audit.log.max.lines", 5000, 10, 100_000));
        }
        return new AgentConfig(agent, connection(settings), prefix, settings.names("elasticsearch.unmanaged.users"),
                settings.syncInterval(), settings.foldWindow(), log);
    }

    private static Connection connection(Settings settings) throws IOException
    {
        List<String> hosts = new ArrayList<>();
        for (String entry : settings.required("elasticsearch.hosts").split(",", -1)) {
            String host = host(entry.strip());
            if (!hosts.contains(host)) {
                hosts.add(host);
            }
        }
        boolean secure = hosts.get(0).startsWith("https:");
        if (hosts.stream().anyMatch(host -> host.startsWith("https:") != secure)) {
            throw new IllegalArgumentException(PREFIX + "elasticsearch.hosts lists the nodes of one cluster, all http or all https");
        }
        String mode = settings.text("elasticsearch.ssl.verification.mode", "full").toLowerCase(Locale.ROOT);
        if (!VERIFICATION_MODES.contains(mode)) {
            throw new IllegalArgumentException(PREFIX + "elasticsearch.ssl.verification.mode is full, certificate or none");
        }
        String ca = settings.optional("elasticsearch.tls.ca");
        String certificate = settings.optional("elasticsearch.tls.cert");
        String key = settings.optional("elasticsearch.tls.key");
        if (certificate == null != (key == null)) {
            throw new IllegalArgumentException(PREFIX + "elasticsearch.tls.cert and " + PREFIX + "elasticsearch.tls.key go together");
        }
        if (!secure && (ca != null || certificate != null)) {
            throw new IllegalArgumentException("the agent's TLS settings are for https:// nodes");
        }
        if (ca != null && "none".equals(mode)) {
            throw new IllegalArgumentException(PREFIX + "elasticsearch.tls.ca checks the nodes; the verification mode none checks nothing");
        }
        String user = settings.optional("elasticsearch.user");
        String passwordFile = settings.optional("elasticsearch.password.file");
        String apiKeyFile = settings.optional("elasticsearch.api.key.file");
        if (user == null != (passwordFile == null)) {
            throw new IllegalArgumentException(PREFIX + "elasticsearch.user and " + PREFIX + "elasticsearch.password.file go together");
        }
        long ways = (user == null ? 0 : 1) + (apiKeyFile == null ? 0 : 1) + (certificate == null ? 0 : 1);
        if (ways != 1) {
            throw new IllegalArgumentException("the agent signs in one way: with " + PREFIX + "elasticsearch.user and " + PREFIX
                    + "elasticsearch.password.file, with " + PREFIX + "elasticsearch.api.key.file, or with " + PREFIX + "elasticsearch.tls.cert and "
                    + PREFIX + "elasticsearch.tls.key");
        }
        String passphraseFile = settings.optional("elasticsearch.tls.key.passphrase.file");
        if (passphraseFile != null && key == null) {
            throw new IllegalArgumentException(PREFIX + "elasticsearch.tls.key.passphrase.file is the passphrase of " + PREFIX + "elasticsearch.tls.key");
        }
        return new Connection(hosts, user, passwordFile == null ? null : Settings.read(passwordFile, StandardCharsets.UTF_8).trim(),
                apiKeyFile == null ? null : Settings.read(apiKeyFile, StandardCharsets.UTF_8).trim(), mode, ca, certificate, key,
                passphraseFile == null ? null : Settings.read(passphraseFile, StandardCharsets.UTF_8).trim(),
                settings.millis("elasticsearch.timeout.ms", 30000, 1, 600_000));
    }

    /** A node as {@code scheme://host:port}, https and 9200 unless said otherwise. */
    private static String host(String entry)
    {
        Matcher matcher = HOST.matcher(entry);
        boolean matches = matcher.matches();
        String scheme = matches && matcher.group(1) != null ? matcher.group(1).toLowerCase(Locale.ROOT) : "https";
        int port = matches && matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 9200;
        if (!matches || !"http".equals(scheme) && !"https".equals(scheme) || port < 1 || port > 65_535) {
            throw new IllegalArgumentException(PREFIX + "elasticsearch.hosts lists nodes as URLs such as https://es1:9200, not " + entry);
        }
        return scheme + "://" + matcher.group(2).toLowerCase(Locale.ROOT) + ":" + port;
    }
}
