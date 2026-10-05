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
        AgentSettings.Builder builder = AgentSettings.builder()
                .server(URI.create(required(configuration, "server.url")))
                .token(token)
                .instance(required(configuration, "instance"))
                .cacheDirectory(Path.of(required(configuration, "cache.dir")))
                .agentVersion(HdfsAgentVersion.value())
                .timeouts(duration(configuration, "connect.timeout.ms", 5000), duration(configuration, "read.timeout.ms", 8000))
                .refreshInterval(duration(configuration, "refresh.interval.ms", 30000));
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

    AgentSettings agent()
    {
        return agent;
    }

    boolean nativeFallback()
    {
        return nativeFallback;
    }
}
