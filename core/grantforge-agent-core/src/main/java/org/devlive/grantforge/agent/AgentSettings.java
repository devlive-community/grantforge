// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * How an agent reaches its server and handles policies and events; built with {@link #builder}, validated when built.
 * Immutable.
 */
public final class AgentSettings
{
    /** The most events the server takes in one batch. */
    public static final int MAX_BATCH = 1000;

    static final Pattern INSTANCE = Pattern.compile("\\p{Graph}{1,128}");

    private final URI server;
    private final String token;
    private final String instance;
    private final String host;
    private final String agentVersion;
    private final Path cacheDirectory;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration refreshInterval;
    private final int auditBatchSize;
    private final Duration auditFlushInterval;
    private final int auditQueueCapacity;
    private final long spoolLimitBytes;
    private final @Nullable SigningKey trustedKey;

    AgentSettings(Builder builder, URI server, String token, String instance, Path cacheDirectory)
    {
        this.server = server;
        this.token = token;
        this.instance = instance;
        this.host = builder.host == null ? localHost() : builder.host;
        this.agentVersion = builder.agentVersion;
        this.cacheDirectory = cacheDirectory;
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.refreshInterval = builder.refreshInterval;
        this.auditBatchSize = builder.auditBatchSize;
        this.auditFlushInterval = builder.auditFlushInterval;
        this.auditQueueCapacity = builder.auditQueueCapacity;
        this.spoolLimitBytes = builder.spoolLimitBytes;
        this.trustedKey = builder.trustedKey;
    }

    /**
     * Starts settings.
     *
     * @return a builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    private static String localHost()
    {
        try {
            return InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException unknown) {
            return "unknown";
        }
    }

    /**
     * Returns the server's address, such as {@code https://grantforge.example.com/}.
     *
     * @return the address
     */
    public URI server()
    {
        return server;
    }

    /**
     * Returns the service's agent token, as the console issues it.
     *
     * @return the token
     */
    public String token()
    {
        return token;
    }

    /**
     * Returns the name this agent gives itself, unique within its service, such as {@code namenode-1:8020}.
     *
     * @return the name
     */
    public String instance()
    {
        return instance;
    }

    /**
     * Returns where the agent runs.
     *
     * @return the host name
     */
    public String host()
    {
        return host;
    }

    /**
     * Returns the agent's version, as heartbeats report it.
     *
     * @return the version
     */
    public String agentVersion()
    {
        return agentVersion;
    }

    /**
     * Returns where the last snapshot and the events not yet sent are kept.
     *
     * @return the directory
     */
    public Path cacheDirectory()
    {
        return cacheDirectory;
    }

    /**
     * Returns how long to wait for a connection to the server.
     *
     * @return the time
     */
    public Duration connectTimeout()
    {
        return connectTimeout;
    }

    /**
     * Returns how long to wait for the server's answer.
     *
     * @return the time
     */
    public Duration readTimeout()
    {
        return readTimeout;
    }

    /**
     * Returns the time between heartbeats until the server sets it, and while it cannot be reached.
     *
     * @return the time
     */
    public Duration refreshInterval()
    {
        return refreshInterval;
    }

    /**
     * Returns the most events sent in one batch.
     *
     * @return the count, at most {@value #MAX_BATCH}
     */
    public int auditBatchSize()
    {
        return auditBatchSize;
    }

    /**
     * Returns how often events are sent when there are fewer than a batch.
     *
     * @return the time
     */
    public Duration auditFlushInterval()
    {
        return auditFlushInterval;
    }

    /**
     * Returns how many events wait in memory; beyond that, recording drops events rather than slow the system down.
     *
     * @return the count
     */
    public int auditQueueCapacity()
    {
        return auditQueueCapacity;
    }

    /**
     * Returns how much disk the events that could not be sent may take; beyond that the oldest are dropped.
     *
     * @return the size in bytes
     */
    public long spoolLimitBytes()
    {
        return spoolLimitBytes;
    }

    /**
     * Returns the server's public key, when it is given rather than fetched from the server on first contact.
     *
     * @return the key, or {@code null}
     */
    public @Nullable SigningKey trustedKey()
    {
        return trustedKey;
    }

    /** Collects settings. */
    public static final class Builder
    {
        @Nullable URI server;
        @Nullable String token;
        @Nullable String instance;
        @Nullable String host;
        String agentVersion = "unknown";
        @Nullable Path cacheDirectory;
        Duration connectTimeout = Duration.ofSeconds(10);
        Duration readTimeout = Duration.ofSeconds(30);
        Duration refreshInterval = Duration.ofSeconds(30);
        int auditBatchSize = 500;
        Duration auditFlushInterval = Duration.ofSeconds(5);
        int auditQueueCapacity = 10_000;
        long spoolLimitBytes = 64L * 1024 * 1024;
        @Nullable SigningKey trustedKey;

        Builder()
        {
        }

        /**
         * Sets the server's address.
         *
         * @param value an http or https address
         * @return this builder
         */
        public Builder server(URI value)
        {
            this.server = value;
            return this;
        }

        /**
         * Sets the agent token.
         *
         * @param value the token
         * @return this builder
         */
        public Builder token(String value)
        {
            this.token = value;
            return this;
        }

        /**
         * Sets the name the agent gives itself.
         *
         * @param value 1-128 visible characters, unique within the service
         * @return this builder
         */
        public Builder instance(String value)
        {
            this.instance = value;
            return this;
        }

        /**
         * Sets where the agent runs; the local host name by default.
         *
         * @param value the host
         * @return this builder
         */
        public Builder host(String value)
        {
            this.host = value;
            return this;
        }

        /**
         * Sets the agent's version.
         *
         * @param value the version
         * @return this builder
         */
        public Builder agentVersion(String value)
        {
            this.agentVersion = value;
            return this;
        }

        /**
         * Sets where the snapshot and unsent events are kept.
         *
         * @param value a directory the agent may write; created if missing
         * @return this builder
         */
        public Builder cacheDirectory(Path value)
        {
            this.cacheDirectory = value;
            return this;
        }

        /**
         * Sets the connection and answer timeouts.
         *
         * @param connect how long to wait for a connection
         * @param read how long to wait for an answer
         * @return this builder
         */
        public Builder timeouts(Duration connect, Duration read)
        {
            this.connectTimeout = connect;
            this.readTimeout = read;
            return this;
        }

        /**
         * Sets the time between heartbeats until the server sets it.
         *
         * @param value the time
         * @return this builder
         */
        public Builder refreshInterval(Duration value)
        {
            this.refreshInterval = value;
            return this;
        }

        /**
         * Sets how events are batched.
         *
         * @param batchSize the most events per batch, 1 to {@value #MAX_BATCH}
         * @param flushInterval how often a partial batch is sent
         * @param queueCapacity how many events wait in memory
         * @return this builder
         */
        public Builder audit(int batchSize, Duration flushInterval, int queueCapacity)
        {
            this.auditBatchSize = batchSize;
            this.auditFlushInterval = flushInterval;
            this.auditQueueCapacity = queueCapacity;
            return this;
        }

        /**
         * Sets how much disk unsent events may take.
         *
         * @param value the size in bytes
         * @return this builder
         */
        public Builder spoolLimitBytes(long value)
        {
            this.spoolLimitBytes = value;
            return this;
        }

        /**
         * Pins the server's public key instead of fetching it on first contact.
         *
         * @param value the key
         * @return this builder
         */
        public Builder trustedKey(SigningKey value)
        {
            this.trustedKey = value;
            return this;
        }

        /**
         * Checks and builds the settings.
         *
         * @return the settings
         * @throws IllegalArgumentException if a setting is missing or out of range
         */
        public AgentSettings build()
        {
            URI address = server;
            if (address == null || !("http".equals(address.getScheme()) || "https".equals(address.getScheme())) || address.getHost() == null) {
                throw new IllegalArgumentException("the server must be an http or https address");
            }
            String secret = token;
            if (secret == null || blank(secret)) {
                throw new IllegalArgumentException("the agent token is missing");
            }
            String name = instance;
            if (name == null || !INSTANCE.matcher(name).matches()) {
                throw new IllegalArgumentException("the instance name must be 1-128 visible characters");
            }
            Path directory = cacheDirectory;
            if (directory == null) {
                throw new IllegalArgumentException("the cache directory is missing");
            }
            positive(connectTimeout, "connect timeout");
            positive(readTimeout, "read timeout");
            positive(refreshInterval, "refresh interval");
            positive(auditFlushInterval, "audit flush interval");
            if (auditBatchSize < 1 || auditBatchSize > MAX_BATCH) {
                throw new IllegalArgumentException("the audit batch size must be 1 to " + MAX_BATCH);
            }
            if (auditQueueCapacity < auditBatchSize) {
                throw new IllegalArgumentException("the audit queue must hold at least one batch");
            }
            if (spoolLimitBytes < 0) {
                throw new IllegalArgumentException("the spool limit must not be negative");
            }
            return new AgentSettings(this, address, secret.trim(), name, directory);
        }

        private static boolean blank(String value)
        {
            for (int index = 0; index < value.length(); index++) {
                if (!Character.isWhitespace(value.charAt(index))) {
                    return false;
                }
            }
            return true;
        }

        private static void positive(Duration value, String what)
        {
            if (value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException("the " + what + " must be positive");
            }
        }
    }
}
