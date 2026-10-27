// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.sync.Settings;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The agent's settings, named {@code grantforge.*} in its properties file: the GrantForge server and token, the
 * ClickHouse servers and how the agent signs in to them, the roles it keeps, how often it brings ClickHouse in step,
 * and how it reads the query log.
 *
 * @param agent the signed-policy agent's settings
 * @param connection the ClickHouse servers and the agent's sign-in
 * @param rolePrefix the start of the names of the roles the agent keeps, one per user
 * @param unmanaged the users the agent leaves alone, besides itself
 * @param syncInterval how often the agent brings ClickHouse in step
 * @param foldWindow how long identical allowed accesses are folded into one event; zero sends each
 * @param queryLog how the agent reads the query log, or {@code null} when it does not
 */
record AgentConfig(AgentSettings agent, Connection connection, String rolePrefix, Set<String> unmanaged, Duration syncInterval,
        Duration foldWindow, @Nullable QueryLog queryLog)
{
    /** The prefix of the settings. */
    static final String PREFIX = Settings.PREFIX;

    /** What role prefixes may hold, so that each role name stays readable in ClickHouse's own statements. */
    private static final Pattern ROLE_PREFIX = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,62}_");

    /** Copies the users. */
    AgentConfig
    {
        unmanaged = Set.copyOf(unmanaged);
    }

    /**
     * The ClickHouse servers and how the agent signs in.
     *
     * @param urls the servers' HTTP interfaces, in the order they are tried
     * @param user the agent's ClickHouse user
     * @param password its password, or {@code null} when it signs in with a certificate or without a password
     * @param ca the PEM CA certificate that verifies the servers, or {@code null} for Java's
     * @param certificate the PEM client certificate it signs in with, or {@code null}
     * @param key the client certificate's PEM private key, or {@code null}
     * @param timeout how long a request may take
     */
    record Connection(List<String> urls, String user, @Nullable String password, @Nullable String ca, @Nullable String certificate,
            @Nullable String key, Duration timeout)
    {
        /** Copies the servers. */
        Connection
        {
            urls = List.copyOf(urls);
        }

        @Override
        public String toString()
        {
            return "Connection[urls=" + urls + ", user=" + user + ", certificate=" + certificate + "]";
        }
    }

    /**
     * How the agent reads the query log.
     *
     * @param cluster the cluster whose replicas' logs are read, or {@code null} for the server the agent asks
     * @param delay how far behind the present the agent reads, so that ClickHouse has written the log
     * @param interval how often it reads
     * @param batch the most entries one read takes
     */
    record QueryLog(@Nullable String cluster, Duration delay, Duration interval, int batch)
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
        Settings settings = new Settings(values, "ClickHouse");
        AgentSettings agent = settings.agent(version);
        String prefix = settings.rolePrefix("clickhouse.role.prefix",
                ROLE_PREFIX.asMatchPredicate(),
                "start with a letter, hold letters, digits and underscores, end with an underscore and be at most 64"
                        + " characters, such as grantforge_");
        Set<String> unmanaged = settings.names("clickhouse.unmanaged.users");
        QueryLog log = null;
        if (settings.flag("audit.query.log", true)) {
            log = new QueryLog(settings.optional("audit.query.log.cluster"),
                    settings.millis("audit.query.log.delay.ms", 10000, 0, 600_000),
                    settings.millis("audit.query.log.interval.ms", 5000, 1000, 600_000),
                    (int) settings.number("audit.query.log.batch", 1000, 1, 100_000));
        }
        return new AgentConfig(agent, connection(settings), prefix, unmanaged,
                settings.syncInterval(), settings.foldWindow(), log);
    }

    private static Connection connection(Settings settings) throws IOException
    {
        List<String> urls = new ArrayList<>();
        for (String entry : settings.required("clickhouse.urls").split(",", -1)) {
            String url = entry.strip();
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            urls.add(server(url));
        }
        String certificate = settings.optional("clickhouse.tls.cert");
        String key = settings.optional("clickhouse.tls.key");
        if (certificate == null != (key == null)) {
            throw new IllegalArgumentException(PREFIX + "clickhouse.tls.cert and " + PREFIX + "clickhouse.tls.key go together");
        }
        String passwordFile = settings.optional("clickhouse.password.file");
        if (certificate != null && passwordFile != null) {
            throw new IllegalArgumentException("the agent signs in with " + PREFIX + "clickhouse.tls.cert or " + PREFIX
                    + "clickhouse.password.file, not both");
        }
        String ca = settings.optional("clickhouse.tls.ca");
        if ((certificate != null || ca != null) && urls.stream().anyMatch(url -> url.startsWith("http:"))) {
            throw new IllegalArgumentException(PREFIX + "clickhouse.tls.* need https:// servers");
        }
        return new Connection(urls, settings.required("clickhouse.user"), passwordFile == null ? null : Settings.read(passwordFile, StandardCharsets.UTF_8).trim(),
                ca, certificate, key, settings.millis("clickhouse.timeout.ms", 30000, 1, 600_000));
    }

    /** An http or https URL with a host and nothing after it, its scheme in lower case. */
    private static String server(String url)
    {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (("http".equals(scheme) || "https".equals(scheme)) && uri.getHost() != null && (uri.getPath() == null || uri.getPath().isEmpty())
                    && uri.getQuery() == null && uri.getFragment() == null && uri.getUserInfo() == null) {
                return scheme + url.substring(scheme.length());
            }
        }
        catch (URISyntaxException wrong) {
            throw new IllegalArgumentException(PREFIX + "clickhouse.urls holds " + url + ", which is no URL", wrong);
        }
        throw new IllegalArgumentException(PREFIX + "clickhouse.urls lists HTTP interfaces such as https://clickhouse1:8443, not " + url);
    }
}
