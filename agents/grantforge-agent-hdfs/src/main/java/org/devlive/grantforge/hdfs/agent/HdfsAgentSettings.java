// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs.agent;

import org.apache.hadoop.conf.Configuration;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.SigningKey;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Reads the NameNode's agent settings; secrets and an optional pinned signing key come from local files. */
final class HdfsAgentSettings
{
    static final String PREFIX = "grantforge.hdfs.";

    private final AgentSettings agent;
    private final boolean nativeFallback;

    private HdfsAgentSettings(AgentSettings agent, boolean nativeFallback)
    {
        this.agent = agent;
        this.nativeFallback = nativeFallback;
    }

    static HdfsAgentSettings read(Configuration configuration) throws IOException
    {
        if (!configuration.getBoolean("dfs.permissions.enabled", true)) {
            throw new IllegalArgumentException("dfs.permissions.enabled must be true for the NameNode to call the agent");
        }
        if (!configuration.getTrimmed("dfs.namenode.inode.attributes.provider.bypass.users", "").isEmpty()) {
            throw new IllegalArgumentException("dfs.namenode.inode.attributes.provider.bypass.users must be empty for enforcement");
        }
        String token = Files.readString(Path.of(required(configuration, "token.file")), StandardCharsets.UTF_8).strip();
        int auditBatch = (int) number(configuration, "audit.batch.size", 500, 1, AgentSettings.MAX_BATCH);
        int auditQueue = (int) number(configuration, "audit.queue.capacity", 10000, 1, 1_000_000);
        if (auditQueue < auditBatch) {
            throw new IllegalArgumentException(PREFIX + "audit.queue.capacity must be at least " + PREFIX + "audit.batch.size");
        }
        AgentSettings.Builder builder = AgentSettings.builder()
                .server(URI.create(required(configuration, "server.url")))
                .token(token)
                .instance(required(configuration, "instance"))
                .cacheDirectory(Path.of(required(configuration, "cache.dir")))
                .agentVersion(HdfsAgentVersion.value())
                .timeouts(duration(configuration, "connect.timeout.ms", 5000), duration(configuration, "read.timeout.ms", 8000))
                .refreshInterval(duration(configuration, "refresh.interval.ms", 30000))
                // Queue and disk bounds keep audit outages from exhausting the NameNode; a zero spool limit explicitly disables it.
                .audit(auditBatch, Duration.ofMillis(number(configuration, "audit.flush.interval.ms", 5000, 1, Integer.MAX_VALUE)), auditQueue)
                .spoolLimitBytes(number(configuration, "audit.spool.limit.bytes", 64L * 1024 * 1024, 0, Long.MAX_VALUE));
        String keyFile = configuration.getTrimmed(PREFIX + "signing.key.file");
        if (keyFile != null && !keyFile.isEmpty()) {
            builder.trustedKey(SigningKey.of(Files.readString(Path.of(keyFile), StandardCharsets.US_ASCII)));
        }
        String fallback = configuration.getTrimmed(PREFIX + "native.fallback", "false");
        if (!"true".equalsIgnoreCase(fallback) && !"false".equalsIgnoreCase(fallback)) {
            throw new IllegalArgumentException(PREFIX + "native.fallback must be true or false");
        }
        return new HdfsAgentSettings(builder.build(), Boolean.parseBoolean(fallback));
    }

    private static String required(Configuration configuration, String key)
    {
        String value = configuration.getTrimmed(PREFIX + key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("missing NameNode setting " + PREFIX + key);
        }
        return value;
    }

    private static Duration duration(Configuration configuration, String key, long fallback)
    {
        long milliseconds = configuration.getLong(PREFIX + key, fallback);
        if (milliseconds < 1 || ("refresh.interval.ms".equals(key) && milliseconds < 1000)) {
            throw new IllegalArgumentException(PREFIX + key + " must be at least "
                    + ("refresh.interval.ms".equals(key) ? 1000 : 1) + " milliseconds");
        }
        if (!"refresh.interval.ms".equals(key) && milliseconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(PREFIX + key + " must not exceed " + Integer.MAX_VALUE + " milliseconds");
        }
        return Duration.ofMillis(milliseconds);
    }

    private static long number(Configuration configuration, String key, long fallback, long minimum, long maximum)
    {
        String name = PREFIX + key;
        long value;
        try {
            value = configuration.getLong(name, fallback);
        }
        catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(name + " must be an integer", invalid);
        }
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    AgentSettings agent()
    {
        return agent;
    }

    boolean nativeFallback()
    {
        return nativeFallback;
    }
}
