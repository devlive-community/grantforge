// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.SigningKey;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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
    static final String PREFIX = "grantforge.";

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

        String prefix = values.getOrDefault(PREFIX + "clickhouse.role.prefix", "grantforge_").trim();
        if (!ROLE_PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException(PREFIX + "clickhouse.role.prefix must start with a letter, hold letters, digits and underscores,"
                    + " end with an underscore and be at most 64 characters, such as grantforge_");
        }
        Set<String> unmanaged = new LinkedHashSet<>();
        String listed = optional(values, "clickhouse.unmanaged.users");
        if (listed != null) {
            for (String user : listed.split(",", -1)) {
                if (!user.isBlank()) {
                    unmanaged.add(user.strip());
                }
            }
        }
        QueryLog log = null;
        if (Boolean.parseBoolean(values.getOrDefault(PREFIX + "audit.query.log", "true").trim())) {
            log = new QueryLog(optional(values, "audit.query.log.cluster"),
                    Duration.ofMillis(number(values, "audit.query.log.delay.ms", 10000, 0, 600_000)),
                    Duration.ofMillis(number(values, "audit.query.log.interval.ms", 5000, 1000, 600_000)),
                    (int) number(values, "audit.query.log.batch", 1000, 1, 100_000));
        }
        return new AgentConfig(agent.build(), connection(values), prefix, unmanaged,
                Duration.ofMillis(number(values, "sync.interval.ms", 10000, 1000, 3_600_000)),
                Duration.ofMillis(number(values, "audit.fold.window.ms", 10000, 0, 3_600_000)), log);
    }

    private static Connection connection(Map<String, String> values) throws IOException
    {
        List<String> urls = new ArrayList<>();
        for (String entry : required(values, "clickhouse.urls").split(",", -1)) {
            String url = entry.strip();
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            urls.add(server(url));
        }
        String certificate = optional(values, "clickhouse.tls.cert");
        String key = optional(values, "clickhouse.tls.key");
        if (certificate == null != (key == null)) {
            throw new IllegalArgumentException(PREFIX + "clickhouse.tls.cert and " + PREFIX + "clickhouse.tls.key go together");
        }
        String passwordFile = optional(values, "clickhouse.password.file");
        if (certificate != null && passwordFile != null) {
            throw new IllegalArgumentException("the agent signs in with " + PREFIX + "clickhouse.tls.cert or " + PREFIX
                    + "clickhouse.password.file, not both");
        }
        String ca = optional(values, "clickhouse.tls.ca");
        if ((certificate != null || ca != null) && urls.stream().anyMatch(url -> url.startsWith("http:"))) {
            throw new IllegalArgumentException(PREFIX + "clickhouse.tls.* need https:// servers");
        }
        return new Connection(urls, required(values, "clickhouse.user"), passwordFile == null ? null : read(passwordFile, StandardCharsets.UTF_8).trim(),
                ca, certificate, key, Duration.ofMillis(number(values, "clickhouse.timeout.ms", 30000, 1, 600_000)));
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

    private static String read(String path, Charset charset) throws IOException
    {
        return new String(Files.readAllBytes(Paths.get(path)), charset);
    }

    private static String required(Map<String, String> values, String key)
    {
        String value = optional(values, key);
        if (value == null) {
            throw new IllegalArgumentException("missing ClickHouse agent setting " + PREFIX + key);
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
