// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent.sync;

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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The {@code grantforge.*} settings of an agent, as its settings file and the system properties give them. It reads the
 * settings every agent has, those of the GrantForge agent within and of its sync, and helps the agent read its own; a
 * setting that is missing or invalid is named in the error.
 */
public final class Settings
{
    /** What every setting's name starts with. */
    public static final String PREFIX = "grantforge.";

    private final Map<String, String> values;
    private final String system;

    /**
     * Wraps the settings.
     *
     * @param values the settings by their full names, such as {@code grantforge.server.url}
     * @param system the protected system's name, such as {@code ClickHouse}, for the errors
     */
    public Settings(Map<String, String> values, String system)
    {
        this.values = Map.copyOf(values);
        this.system = system;
    }

    /**
     * Reads the settings of the GrantForge agent within: the server, the token, the instance, the cache, the timeouts,
     * the policy refresh, the audit batches and spool, and the key the snapshots are signed with.
     *
     * @param version the agent's version
     * @return the settings
     * @throws IOException when the token or key file cannot be read
     * @throws IllegalArgumentException when a setting is missing or invalid
     */
    public AgentSettings agent(String version) throws IOException
    {
        if (version.isBlank() || version.length() > 64) {
            throw new IllegalArgumentException("the agent version must be 1 to 64 characters");
        }
        int batch = (int) number("audit.batch.size", 500, 1, AgentSettings.MAX_BATCH);
        int queue = (int) number("audit.queue.capacity", 10000, 1, 1_000_000);
        if (queue < batch) {
            throw new IllegalArgumentException(PREFIX + "audit.queue.capacity must be at least " + PREFIX + "audit.batch.size");
        }
        AgentSettings.Builder agent = AgentSettings.builder().server(URI.create(required("server.url")))
                .token(read(required("token.file"), StandardCharsets.UTF_8).trim()).instance(required("instance"))
                .cacheDirectory(Paths.get(required("cache.dir"))).agentVersion(version)
                .timeouts(Duration.ofMillis(number("connect.timeout.ms", 5000, 1, Integer.MAX_VALUE)),
                        Duration.ofMillis(number("read.timeout.ms", 8000, 1, Integer.MAX_VALUE)))
                .refreshInterval(Duration.ofMillis(number("refresh.interval.ms", 30000, 1000, Long.MAX_VALUE)))
                .audit(batch, Duration.ofMillis(number("audit.flush.interval.ms", 5000, 1, Integer.MAX_VALUE)), queue)
                .spoolLimitBytes(number("audit.spool.limit.bytes", 64L * 1024 * 1024, 0, Long.MAX_VALUE));
        String key = optional("signing.key.file");
        if (key != null) {
            agent.trustedKey(SigningKey.of(read(key, StandardCharsets.US_ASCII)));
        }
        return agent.build();
    }

    /**
     * Returns how often the system is brought in step with the policies: {@code grantforge.sync.interval.ms}, ten
     * seconds unless set.
     *
     * @return the interval
     */
    public Duration syncInterval()
    {
        return millis("sync.interval.ms", 10000, 1000, 3_600_000);
    }

    /**
     * Returns the window in which identical allowed accesses fold into one event: {@code grantforge.audit.fold.window.ms},
     * ten seconds unless set, none at zero.
     *
     * @return the window
     */
    public Duration foldWindow()
    {
        return millis("audit.fold.window.ms", 10000, 0, 3_600_000);
    }

    /**
     * Reads what the names of the agent's roles start with, which tells them from everyone else's.
     *
     * @param key the setting, such as {@code clickhouse.role.prefix}
     * @param valid what the system accepts, such as a pattern's {@code asMatchPredicate()}
     * @param rule the rule in words, ending the error {@code grantforge.<key> must ...}
     * @return the prefix, {@code grantforge_} unless set
     * @throws IllegalArgumentException when the system would not accept it
     */
    public String rolePrefix(String key, Predicate<String> valid, String rule)
    {
        String prefix = text(key, "grantforge_");
        if (!valid.test(prefix)) {
            throw new IllegalArgumentException(PREFIX + key + " must " + rule);
        }
        return prefix;
    }

    /**
     * Reads a setting the agent cannot run without.
     *
     * @param key the setting without {@code grantforge.}
     * @return its value without surrounding white space
     * @throws IllegalArgumentException when it is missing or blank
     */
    public String required(String key)
    {
        String value = optional(key);
        if (value == null) {
            throw new IllegalArgumentException("missing " + system + " agent setting " + PREFIX + key);
        }
        return value;
    }

    /**
     * Reads a setting that may be left out.
     *
     * @param key the setting without {@code grantforge.}
     * @return its value without surrounding white space, or {@code null} when it is missing or blank
     */
    public @Nullable String optional(String key)
    {
        String configured = values.get(PREFIX + key);
        String value = configured == null ? "" : configured.trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * Reads a setting with a default.
     *
     * @param key the setting without {@code grantforge.}
     * @param fallback the value when it is missing
     * @return its value without surrounding white space, blank when set blank
     */
    public String text(String key, String fallback)
    {
        return values.getOrDefault(PREFIX + key, fallback).trim();
    }

    /**
     * Reads a switch.
     *
     * @param key the setting without {@code grantforge.}
     * @param fallback the value when it is missing
     * @return whether it is on: only {@code true}, in any case, turns it on
     */
    public boolean flag(String key, boolean fallback)
    {
        return Boolean.parseBoolean(text(key, Boolean.toString(fallback)));
    }

    /**
     * Reads a whole number within bounds.
     *
     * @param key the setting without {@code grantforge.}
     * @param fallback the value when it is missing
     * @param minimum the least value allowed
     * @param maximum the greatest value allowed
     * @return the number
     * @throws IllegalArgumentException when it is no integer or out of bounds
     */
    public long number(String key, long fallback, long minimum, long maximum)
    {
        String configured = optional(key);
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

    /**
     * Reads a duration given in milliseconds.
     *
     * @param key the setting without {@code grantforge.}, usually ending in {@code .ms}
     * @param fallback the milliseconds when it is missing
     * @param minimum the fewest milliseconds allowed
     * @param maximum the most milliseconds allowed
     * @return the duration
     */
    public Duration millis(String key, long fallback, long minimum, long maximum)
    {
        return Duration.ofMillis(number(key, fallback, minimum, maximum));
    }

    /**
     * Reads a list of names separated by commas, such as users or databases.
     *
     * @param key the setting without {@code grantforge.}
     * @return the names in their order, without blanks and repetitions; none when the setting is missing
     */
    public Set<String> names(String key)
    {
        Set<String> names = new LinkedHashSet<>();
        String listed = optional(key);
        if (listed != null) {
            for (String name : listed.split(",", -1)) {
                if (!name.isBlank()) {
                    names.add(name.strip());
                }
            }
        }
        return names;
    }

    /**
     * Reads a file a setting names, such as a token or a password file.
     *
     * @param path the file
     * @param charset its encoding
     * @return its content
     * @throws IOException when it cannot be read
     */
    public static String read(String path, Charset charset) throws IOException
    {
        return new String(Files.readAllBytes(Paths.get(path)), charset);
    }
}
