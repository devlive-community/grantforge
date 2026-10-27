// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.sync.Settings;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    static final String PREFIX = Settings.PREFIX;

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
        Settings settings = new Settings(values, "OpenSearch");
        AgentSettings agent = settings.agent(version);
        String prefix = settings.rolePrefix("opensearch.role.prefix",
                ROLE_PREFIX.asMatchPredicate(),
                "start with a lowercase letter, hold lowercase letters, digits, hyphens and underscores, end with an"
                        + " underscore and be at most 32 characters, such as grantforge_");
        AuditLogSettings log = null;
        if (settings.flag("audit.log", true)) {
            String index = settings.text("audit.log.index", "security-auditlog-*");
            if (index.isEmpty() || index.contains(",") || index.contains("/") || index.startsWith("-")) {
                throw new IllegalArgumentException(PREFIX + "audit.log.index names the audit log's indices as one name or pattern, such as"
                        + " security-auditlog-*");
            }
            log = new AuditLogSettings(index, settings.millis("audit.log.interval.ms", 5000, 1000, 600_000),
                    (int) settings.number("audit.log.max.documents", 1000, 10, 10_000));
        }
        return new AgentConfig(agent, connection(settings), prefix, settings.names("opensearch.unmanaged.users"),
                settings.syncInterval(), settings.foldWindow(), log);
    }

    private static Connection connection(Settings settings) throws IOException
    {
        List<String> hosts = new ArrayList<>();
        for (String entry : settings.required("opensearch.hosts").split(",", -1)) {
            String host = host(entry.strip());
            if (!hosts.contains(host)) {
                hosts.add(host);
            }
        }
        boolean secure = hosts.get(0).startsWith("https:");
        if (hosts.stream().anyMatch(host -> host.startsWith("https:") != secure)) {
            throw new IllegalArgumentException(PREFIX + "opensearch.hosts lists the nodes of one cluster, all http or all https");
        }
        String mode = settings.text("opensearch.ssl.verification.mode", "full").toLowerCase(Locale.ROOT);
        if (!VERIFICATION_MODES.contains(mode)) {
            throw new IllegalArgumentException(PREFIX + "opensearch.ssl.verification.mode is full, certificate or none");
        }
        String ca = settings.optional("opensearch.tls.ca");
        String certificate = settings.optional("opensearch.tls.cert");
        String key = settings.optional("opensearch.tls.key");
        if (certificate == null != (key == null)) {
            throw new IllegalArgumentException(PREFIX + "opensearch.tls.cert and " + PREFIX + "opensearch.tls.key go together");
        }
        if (!secure && (ca != null || certificate != null)) {
            throw new IllegalArgumentException("the agent's TLS settings are for https:// nodes");
        }
        if (ca != null && "none".equals(mode)) {
            throw new IllegalArgumentException(PREFIX + "opensearch.tls.ca checks the nodes; the verification mode none checks nothing");
        }
        String user = settings.optional("opensearch.user");
        String passwordFile = settings.optional("opensearch.password.file");
        if (certificate == null) {
            if (user == null || passwordFile == null) {
                throw new IllegalArgumentException("the agent signs in with " + PREFIX + "opensearch.user and " + PREFIX + "opensearch.password.file, or"
                        + " with " + PREFIX + "opensearch.tls.cert and " + PREFIX + "opensearch.tls.key");
            }
        }
        else if (user != null || passwordFile != null) {
            throw new IllegalArgumentException("the agent signs in with " + PREFIX + "opensearch.tls.cert or " + PREFIX + "opensearch.user, not both");
        }
        String passphraseFile = settings.optional("opensearch.tls.key.passphrase.file");
        if (passphraseFile != null && key == null) {
            throw new IllegalArgumentException(PREFIX + "opensearch.tls.key.passphrase.file is the passphrase of " + PREFIX + "opensearch.tls.key");
        }
        return new Connection(hosts, user, passwordFile == null ? null : Settings.read(passwordFile, StandardCharsets.UTF_8).trim(), mode, ca, certificate,
                key, passphraseFile == null ? null : Settings.read(passphraseFile, StandardCharsets.UTF_8).trim(),
                settings.millis("opensearch.timeout.ms", 30000, 1, 600_000));
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
}
