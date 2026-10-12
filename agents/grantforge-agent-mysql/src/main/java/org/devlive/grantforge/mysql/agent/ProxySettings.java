// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.devlive.grantforge.agent.AgentSettings;
import org.devlive.grantforge.agent.SigningKey;
import org.devlive.grantforge.mysql.agent.proxy.Backend;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLException;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * The proxy's settings, named {@code grantforge.*} in its properties file: the GrantForge server and token of the
 * agent, where the proxy listens, the MySQL server behind it with TLS on either side, and the metadata user.
 *
 * @param agent the signed-policy agent's settings
 * @param listen where the proxy listens
 * @param backend the MySQL server and TLS
 * @param metadataUrl the MySQL server for the metadata user, as MariaDB Connector/J takes it
 * @param metadata the metadata user's driver options, credentials included
 * @param keep how long schema lookups are kept
 * @param checkThreads how many threads check statements
 */
public record ProxySettings(AgentSettings agent, InetSocketAddress listen, Backend backend, String metadataUrl, Properties metadata,
        Duration keep, int checkThreads)
{
    /** The prefix of the settings. */
    public static final String PREFIX = "grantforge.";

    /** Copies the driver options. */
    public ProxySettings
    {
        metadata = (Properties) metadata.clone();
    }

    /**
     * Returns the metadata user's driver options.
     *
     * @return a copy of them
     */
    @Override
    public Properties metadata()
    {
        return (Properties) metadata.clone();
    }

    /** How the proxy secures its connection to MySQL. */
    enum TlsMode
    {
        DISABLED, REQUIRED, VERIFY_CA, VERIFY_IDENTITY
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
    public static ProxySettings parse(Map<String, String> values, String version) throws IOException
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

        String host = required(values, "mysql.host");
        int port = (int) number(values, "mysql.port", 3306, 1, 65535);
        TlsMode mode = mode(values);
        String ca = optional(values, "mysql.tls.ca");
        if (ca != null && mode == TlsMode.DISABLED) {
            throw new IllegalArgumentException(PREFIX + "mysql.tls.ca needs " + PREFIX + "mysql.tls set to verify-ca or verify-identity");
        }
        String certificate = optional(values, "proxy.tls.cert");
        String privateKey = optional(values, "proxy.tls.key");
        if (certificate == null != (privateKey == null)) {
            throw new IllegalArgumentException(PREFIX + "proxy.tls.cert and " + PREFIX + "proxy.tls.key go together");
        }
        boolean required = Boolean.parseBoolean(values.getOrDefault(PREFIX + "proxy.tls.required", "false").trim());
        Backend backend = new Backend(host, port, backendTls(mode, ca), clientTls(certificate, privateKey), required,
                (int) number(values, "mysql.connect.timeout.ms", 10000, 1, 600_000));

        Properties metadata = new Properties();
        metadata.setProperty("user", required(values, "mysql.metadata.user"));
        String password = optional(values, "mysql.metadata.password.file");
        if (password != null) {
            metadata.setProperty("password", read(password, StandardCharsets.UTF_8).trim());
        }
        metadata.setProperty("allowLocalInfile", "false");
        metadata.setProperty("connectTimeout", Long.toString(number(values, "mysql.connect.timeout.ms", 10000, 1, 600_000)));
        metadata.setProperty("socketTimeout", "30000");
        switch (mode) {
            case DISABLED -> {
                metadata.setProperty("sslMode", "disable");
                metadata.setProperty("allowPublicKeyRetrieval", "true");
            }
            case REQUIRED -> metadata.setProperty("sslMode", "trust");
            case VERIFY_CA -> metadata.setProperty("sslMode", "verify-ca");
            default -> metadata.setProperty("sslMode", "verify-full");
        }
        if (ca != null) {
            metadata.setProperty("serverSslCert", ca);
        }
        String listenHost = optional(values, "proxy.host");
        int listenPort = (int) number(values, "proxy.port", 3307, 0, 65535);
        // Without a host the proxy listens on every address.
        InetSocketAddress listen = listenHost == null ? new InetSocketAddress(listenPort) : new InetSocketAddress(listenHost, listenPort);
        return new ProxySettings(agent.build(), listen, backend,
                "jdbc:mariadb://" + (host.contains(":") ? "[" + host + "]" : host) + ":" + port + "/", metadata,
                Duration.ofSeconds(number(values, "mysql.metadata.cache.seconds", 30, 0, 86_400)),
                (int) number(values, "proxy.check.threads", 8, 1, 256));
    }

    private static TlsMode mode(Map<String, String> values)
    {
        String mode = values.getOrDefault(PREFIX + "mysql.tls", "disabled").trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return TlsMode.valueOf(mode);
        }
        catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(PREFIX + "mysql.tls must be disabled, required, verify-ca or verify-identity", unknown);
        }
    }

    private static @Nullable SslContext backendTls(TlsMode mode, @Nullable String ca) throws SSLException
    {
        if (mode == TlsMode.DISABLED) {
            return null;
        }
        SslContextBuilder builder = SslContextBuilder.forClient();
        if (mode == TlsMode.REQUIRED) {
            // Encrypted but not verified, as MySQL's own --ssl-mode=REQUIRED.
            builder.trustManager(InsecureTrustManagerFactory.INSTANCE).endpointIdentificationAlgorithm(null);
        }
        else {
            if (ca != null) {
                builder.trustManager(new File(ca));
            }
            builder.endpointIdentificationAlgorithm(mode == TlsMode.VERIFY_IDENTITY ? "HTTPS" : null);
        }
        return builder.build();
    }

    private static @Nullable SslContext clientTls(@Nullable String certificate, @Nullable String privateKey) throws SSLException
    {
        if (certificate == null || privateKey == null) {
            return null;
        }
        return SslContextBuilder.forServer(new File(certificate), new File(privateKey)).build();
    }

    private static String read(String path, Charset charset) throws IOException
    {
        return new String(Files.readAllBytes(Paths.get(path)), charset);
    }

    private static String required(Map<String, String> values, String key)
    {
        String value = optional(values, key);
        if (value == null) {
            throw new IllegalArgumentException("missing MySQL proxy setting " + PREFIX + key);
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
