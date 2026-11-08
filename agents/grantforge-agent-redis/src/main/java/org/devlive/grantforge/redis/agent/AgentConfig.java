// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

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
 * The agent's settings, named {@code grantforge.*} in its properties file: the GrantForge server and token, the Redis
 * nodes and how the agent signs in to them, the users it leaves alone, how often it brings Redis in step, and how it
 * reads the ACL logs.
 *
 * @param agent the signed-policy agent's settings
 * @param connection the nodes and the agent's sign-in
 * @param unmanaged the users the agent leaves alone, besides itself
 * @param syncInterval how often the agent brings Redis in step
 * @param foldWindow how long identical allowed accesses are folded into one event; zero sends each
 * @param aclLog how often the agent reads the nodes' ACL logs, or {@code null} when it does not
 */
record AgentConfig(AgentSettings agent, Connection connection, Set<String> unmanaged, Duration syncInterval, Duration foldWindow, @Nullable Duration aclLog)
{
    /** The prefix of the settings. */
    static final String PREFIX = Settings.PREFIX;

    static final String DISABLE = "disable";
    static final String VERIFY_CA = "verify-ca";
    static final String VERIFY_FULL = "verify-full";
    /** How the agent encrypts: not at all, checking the servers' certificates, or their names too. */
    static final List<String> TLS_MODES = List.of(DISABLE, VERIFY_CA, VERIFY_FULL);

    /** A host name, an IPv4 address or a bracketed IPv6 address, with an optional port. */
    private static final Pattern HOST = Pattern.compile("(\\[[0-9A-Fa-f:.]+]|[A-Za-z0-9][A-Za-z0-9.-]*)(?::(\\d{1,5}))?");

    /** Copies the names. */
    AgentConfig
    {
        unmanaged = Set.copyOf(unmanaged);
    }

    /**
     * The nodes and how the agent signs in.
     *
     * @param nodes the nodes as {@code host:port}, in the order they are tried
     * @param discover whether the rest of a cluster and a primary's replicas are found from them
     * @param user the agent's ACL user, or {@code null} for the default user
     * @param password its password, or {@code null} without one
     * @param tlsMode disable, verify-ca or verify-full
     * @param caFile the PEM CA certificates that verify the servers, or {@code null} for Java's
     * @param certificateFile the PEM client certificate, or {@code null} without one
     * @param keyFile its PEM key, or {@code null} when the certificate's file holds it
     * @param keyPassword the password of an encrypted key, or {@code null}
     * @param timeout how long connecting and a command may take
     */
    record Connection(List<String> nodes, boolean discover, @Nullable String user, @Nullable String password, String tlsMode, @Nullable String caFile,
            @Nullable String certificateFile, @Nullable String keyFile, @Nullable String keyPassword, Duration timeout)
    {
        /** Copies the nodes. */
        Connection
        {
            nodes = List.copyOf(nodes);
        }

        @Override
        public String toString()
        {
            return "Connection[nodes=" + nodes + ", user=" + user + ", tlsMode=" + tlsMode + "]";
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
        Settings settings = new Settings(values, "Redis");
        AgentSettings agent = settings.agent(version);
        Duration aclLog = settings.flag("audit.log", true) ? settings.millis("audit.log.interval.ms", 5000, 1000, 600_000) : null;
        return new AgentConfig(agent, connection(settings), settings.names("redis.unmanaged.users"), settings.syncInterval(), settings.foldWindow(), aclLog);
    }

    private static Connection connection(Settings settings) throws IOException
    {
        List<String> nodes = new ArrayList<>();
        for (String entry : settings.required("redis.nodes").split(",", -1)) {
            Matcher matcher = HOST.matcher(entry.strip());
            int port = matcher.matches() && matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 6379;
            if (!matcher.matches() || port < 1 || port > 65_535) {
                throw new IllegalArgumentException(PREFIX + "redis.nodes lists the nodes such as redis1:6379, not " + entry.strip());
            }
            String node = matcher.group(1).toLowerCase(Locale.ROOT) + ":" + port;
            if (!nodes.contains(node)) {
                nodes.add(node);
            }
        }
        String mode = settings.text("redis.tls.mode", DISABLE).toLowerCase(Locale.ROOT);
        if (!TLS_MODES.contains(mode)) {
            throw new IllegalArgumentException(PREFIX + "redis.tls.mode is disable, verify-ca or verify-full, not " + mode);
        }
        String ca = settings.optional("redis.tls.ca.file");
        String certificate = settings.optional("redis.tls.certificate.file");
        String key = settings.optional("redis.tls.key.file");
        if (DISABLE.equals(mode) && (ca != null || certificate != null || key != null)) {
            throw new IllegalArgumentException(PREFIX + "redis.tls.* files are used in verify-ca and verify-full mode only");
        }
        if (key != null && certificate == null) {
            throw new IllegalArgumentException(PREFIX + "redis.tls.key.file signs in with the certificate of " + PREFIX + "redis.tls.certificate.file");
        }
        String passwordFile = settings.optional("redis.password.file");
        String keyPasswordFile = settings.optional("redis.tls.key.password.file");
        return new Connection(nodes, settings.flag("redis.discover", true), settings.optional("redis.user"),
                passwordFile == null ? null : Settings.read(passwordFile, StandardCharsets.UTF_8).trim(), mode, ca, certificate, key,
                keyPasswordFile == null ? null : Settings.read(keyPasswordFile, StandardCharsets.UTF_8).trim(), settings.millis("redis.timeout.ms", 10_000, 1, 600_000));
    }
}
