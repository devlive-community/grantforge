// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis;

import org.devlive.grantforge.plugin.api.LookupException;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.exceptions.JedisConnectionException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to a deployment through Jedis, one connection per node and call, as lookups are rare and each service's settings
 * can change between them. The deployment's nodes are found from the first node given that answers.
 */
class RedisClient
{
    private final RedisSettings settings;

    RedisClient(RedisSettings settings)
    {
        this.settings = settings;
    }

    /**
     * Work on a connected node.
     *
     * @param <T> what it returns
     */
    @FunctionalInterface
    interface Work<T>
    {
        /**
         * Does the work.
         *
         * @param node the connection
         * @return the result
         */
        T run(Jedis node);
    }

    /**
     * Connects to a node, does some work and disconnects.
     *
     * @param <T> what the work returns
     * @param address the node as {@code host:port}
     * @param work the work
     * @return what it returned
     * @throws redis.clients.jedis.exceptions.JedisException when the node cannot be reached, refuses the sign-in or fails a command
     */
    <T> T on(String address, Work<T> work)
    {
        try (Jedis node = connect(HostAndPort.from(address))) {
            return work.run(node);
        }
    }

    /**
     * Connects to a node.
     *
     * @param address the node
     * @return the connection
     */
    Jedis connect(HostAndPort address)
    {
        return new Jedis(address, config());
    }

    /**
     * Builds the client's settings.
     *
     * @return them
     * @throws LookupException when a TLS file cannot be used
     */
    JedisClientConfig config()
    {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder().user(settings.user()).password(settings.password())
                .connectionTimeoutMillis(settings.timeout()).socketTimeoutMillis(settings.timeout()).clientName("grantforge");
        if (!RedisSettings.DISABLE.equals(settings.tlsMode())) {
            SSLParameters parameters = new SSLParameters();
            if (RedisSettings.VERIFY_FULL.equals(settings.tlsMode())) {
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
            }
            builder.ssl(true).sslSocketFactory(tls().getSocketFactory()).sslParameters(parameters);
        }
        return builder.build();
    }

    private SSLContext tls()
    {
        try {
            return RedisTls.context(path(settings.caFile()), path(settings.certificateFile()), path(settings.keyFile()), settings.keyPassword());
        }
        catch (IOException | GeneralSecurityException unusable) {
            throw new LookupException(LookupException.Reason.FAILED, "the TLS files cannot be used: " + unusable.getMessage(), unusable);
        }
    }

    private static @Nullable Path path(@Nullable String file)
    {
        return file == null ? null : Path.of(file);
    }

    /**
     * Finds the deployment's nodes from the first node given that answers.
     *
     * @return what the deployment is and its nodes
     * @throws redis.clients.jedis.exceptions.JedisException when no node given answers, or one refuses the sign-in
     */
    Topology topology()
    {
        JedisConnectionException last = null;
        for (String seed : settings.nodes()) {
            try {
                return on(seed, node -> topology(seed, node));
            }
            catch (JedisConnectionException unreachable) {
                last = unreachable;
            }
        }
        throw last == null ? new JedisConnectionException("no node is given") : last;
    }

    private Topology topology(String self, Jedis seed)
    {
        Map<String, String> server = Topology.fields(seed.info("server"));
        String product = Topology.product(server);
        String version = Topology.version(server);
        if ("cluster".equals(server.get("redis_mode")) || "cluster".equals(server.get("server_mode"))) {
            return new Topology(product, version, true, Topology.clusterNodes(seed.clusterNodes()));
        }
        Map<String, String> replication = Topology.fields(seed.info("replication"));
        Map<String, Boolean> nodes = new LinkedHashMap<>();
        if ("slave".equals(replication.get("role")) && replication.containsKey("master_host")) {
            String primary = replication.get("master_host") + ":" + replication.get("master_port");
            nodes.put(primary, true);
            try {
                on(primary, node -> Topology.replicas(Topology.fields(node.info("replication")))).forEach(replica -> nodes.put(replica, false));
            }
            catch (JedisConnectionException unreachable) {
                nodes.put(self, false);
            }
        }
        else {
            nodes.put(self, true);
            Topology.replicas(replication).forEach(replica -> nodes.put(replica, false));
        }
        List<Topology.Node> found = new ArrayList<>();
        nodes.forEach((address, primary) -> found.add(new Topology.Node(address, primary)));
        return new Topology(product, version, false, found);
    }
}
