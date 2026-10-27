// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

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
 * The agent's settings, named {@code grantforge.*} in its properties file: the GrantForge server and token, the Doris
 * frontends and how the agent signs in to them, the roles it keeps and the catalogs it looks after, how often it brings
 * Doris in step, and how it reads the audit log.
 *
 * @param agent the signed-policy agent's settings
 * @param connection the frontends and the agent's sign-in
 * @param rolePrefix the start of the names of the roles the agent keeps, one per user
 * @param unmanaged the users the agent leaves alone, besides itself
 * @param catalogs the catalogs whose databases and tables the agent grants by name, or none for every catalog
 * @param syncInterval how often the agent brings Doris in step
 * @param foldWindow how long identical allowed accesses are folded into one event; zero sends each
 * @param auditLog how the agent reads the audit log, or {@code null} when it does not
 */
record AgentConfig(AgentSettings agent, Connection connection, String rolePrefix, Set<String> unmanaged, Set<String> catalogs, Duration syncInterval,
        Duration foldWindow, @Nullable AuditLog auditLog)
{
    /** The prefix of the settings. */
    static final String PREFIX = Settings.PREFIX;

    /** The driver's TLS modes, as MariaDB Connector/J names them. */
    static final List<String> SSL_MODES = List.of("disable", "trust", "verify-ca", "verify-full");

    /** What role prefixes may hold, so that each role name stays a plain name in Doris's own statements. */
    private static final Pattern ROLE_PREFIX = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,30}_");

    /** A host name, an IPv4 address or a bracketed IPv6 address, with an optional port. */
    private static final Pattern HOST = Pattern.compile("(\\[[0-9A-Fa-f:.]+]|[A-Za-z0-9][A-Za-z0-9.-]*)(?::(\\d{1,5}))?");

    /** Copies the names. */
    AgentConfig
    {
        unmanaged = Set.copyOf(unmanaged);
        catalogs = Set.copyOf(catalogs);
    }

    /**
     * The frontends and how the agent signs in.
     *
     * @param hosts the frontends' MySQL protocol addresses as {@code host:port}, in the order they are tried
     * @param user the agent's Doris user
     * @param password its password, or {@code null} for a user without one
     * @param sslMode disable, trust, verify-ca or verify-full
     * @param ca the PEM CA certificate that verifies the frontends, or {@code null} for Java's
     * @param timeout how long connecting and a statement may take
     */
    record Connection(List<String> hosts, String user, @Nullable String password, String sslMode, @Nullable String ca, Duration timeout)
    {
        /** Copies the frontends. */
        Connection
        {
            hosts = List.copyOf(hosts);
        }

        @Override
        public String toString()
        {
            return "Connection[hosts=" + hosts + ", user=" + user + ", sslMode=" + sslMode + "]";
        }
    }

    /**
     * How the agent reads the audit log.
     *
     * @param delay how far behind the present the agent reads, so that every frontend has written its batch; {@code null}
     *        to follow the cluster's own {@code audit_plugin_max_batch_interval_sec}
     * @param interval how often it reads
     * @param batch the most entries one read takes
     */
    record AuditLog(@Nullable Duration delay, Duration interval, int batch)
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
        Settings settings = new Settings(values, "Doris");
        AgentSettings agent = settings.agent(version);
        String prefix = settings.rolePrefix("doris.role.prefix", ROLE_PREFIX.asMatchPredicate(),
                "start with a letter, hold letters, digits and underscores, end with an underscore and be at most 32 characters, such as"
                        + " grantforge_");
        AuditLog log = null;
        if (settings.flag("audit.log", true)) {
            String delay = settings.optional("audit.log.delay.ms");
            log = new AuditLog(delay == null ? null : settings.millis("audit.log.delay.ms", 0, 0, 3_600_000),
                    settings.millis("audit.log.interval.ms", 5000, 1000, 600_000),
                    (int) settings.number("audit.log.batch", 1000, 1, 100_000));
        }
        return new AgentConfig(agent, connection(settings), prefix, settings.names("doris.unmanaged.users"), settings.names("doris.catalogs"),
                settings.syncInterval(), settings.foldWindow(), log);
    }

    private static Connection connection(Settings settings) throws IOException
    {
        List<String> hosts = new ArrayList<>();
        for (String entry : settings.required("doris.hosts").split(",", -1)) {
            Matcher matcher = HOST.matcher(entry.strip());
            int port = matcher.matches() && matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 9030;
            if (!matcher.matches() || port < 1 || port > 65_535) {
                throw new IllegalArgumentException(PREFIX + "doris.hosts lists the frontends' MySQL protocol addresses such as fe1:9030, not "
                        + entry.strip());
            }
            String host = matcher.group(1).toLowerCase(Locale.ROOT) + ":" + port;
            if (!hosts.contains(host)) {
                hosts.add(host);
            }
        }
        String mode = settings.text("doris.ssl.mode", "disable").toLowerCase(Locale.ROOT);
        if (!SSL_MODES.contains(mode)) {
            throw new IllegalArgumentException(PREFIX + "doris.ssl.mode is disable, trust, verify-ca or verify-full, not " + mode);
        }
        String ca = settings.optional("doris.tls.ca");
        if (ca != null && !mode.startsWith("verify-")) {
            throw new IllegalArgumentException(PREFIX + "doris.tls.ca verifies the frontends in verify-ca or verify-full mode only");
        }
        String passwordFile = settings.optional("doris.password.file");
        return new Connection(hosts, settings.required("doris.user"),
                passwordFile == null ? null : Settings.read(passwordFile, StandardCharsets.UTF_8).trim(), mode, ca,
                settings.millis("doris.timeout.ms", 30000, 1, 600_000));
    }
}
