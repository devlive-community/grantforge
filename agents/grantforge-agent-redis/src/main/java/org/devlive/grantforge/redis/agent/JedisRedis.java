// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.jspecify.annotations.Nullable;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.util.SafeEncoder;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * The deployment through Jedis: one connection per node, kept open and opened again after a failure, the nodes found
 * from the first node given that answers unless the settings say they are all given. Thread-safe, each node's
 * connection used by one thread at a time.
 */
final class JedisRedis
        implements Redis
{
    private final AgentConfig.Connection settings;
    private final Function<String, Jedis> connect;
    private final Map<String, Link> nodes = new ConcurrentHashMap<>();

    /** A node's connection, and the lock that lets one thread at a time use it. */
    private record Link(Jedis connection, ReentrantLock lock)
    {
    }

    JedisRedis(AgentConfig.Connection settings)
    {
        this(settings, address -> new Jedis(HostAndPort.from(address), config(settings)));
    }

    JedisRedis(AgentConfig.Connection settings, Function<String, Jedis> connect)
    {
        this.settings = settings;
        this.connect = connect;
    }

    /**
     * Builds the client's settings.
     *
     * @param settings the connection settings
     * @return Jedis's settings
     * @throws RedisFailure when a TLS file cannot be used
     */
    static JedisClientConfig config(AgentConfig.Connection settings)
    {
        int timeout = (int) settings.timeout().toMillis();
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder().user(settings.user()).password(settings.password())
                .connectionTimeoutMillis(timeout).socketTimeoutMillis(timeout).clientName("grantforge-agent");
        if (!AgentConfig.DISABLE.equals(settings.tlsMode())) {
            SSLParameters parameters = new SSLParameters();
            if (AgentConfig.VERIFY_FULL.equals(settings.tlsMode())) {
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
            }
            builder.ssl(true).sslSocketFactory(tls(settings).getSocketFactory()).sslParameters(parameters);
        }
        return builder.build();
    }

    private static SSLContext tls(AgentConfig.Connection settings)
    {
        try {
            return Tls.context(path(settings.caFile()), path(settings.certificateFile()), path(settings.keyFile()), settings.keyPassword());
        }
        catch (IOException | GeneralSecurityException unusable) {
            throw new RedisFailure("the TLS files cannot be used: " + unusable.getMessage(), false, unusable);
        }
    }

    private static @Nullable Path path(@Nullable String file)
    {
        return file == null ? null : Path.of(file);
    }

    // A node per node given.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    @Override
    public Topology topology()
    {
        RedisFailure last = null;
        for (String seed : settings.nodes()) {
            try {
                Topology found = discover(seed);
                if (!settings.discover()) {
                    List<Topology.Node> given = new ArrayList<>();
                    for (String node : settings.nodes()) {
                        given.add(new Topology.Node(node, found.primaries().contains(node) || found.nodes().stream().noneMatch(each -> each.address().equals(node))));
                    }
                    return new Topology(found.product(), found.version(), found.cluster(), given);
                }
                return found;
            }
            catch (RedisFailure failed) {
                if (!failed.unreachable()) {
                    throw failed;
                }
                last = failed;
            }
        }
        throw last == null ? new RedisFailure("no node is given", true, null) : last;
    }

    private Topology discover(String seed)
    {
        Map<String, String> server = Topology.fields(text(command(seed, "INFO", "server")));
        String product = Topology.product(server);
        String version = Topology.version(server);
        if ("cluster".equals(server.get("redis_mode")) || "cluster".equals(server.get("server_mode"))) {
            return new Topology(product, version, true, Topology.clusterNodes(text(command(seed, "CLUSTER", "NODES"))));
        }
        Map<String, String> replication = Topology.fields(text(command(seed, "INFO", "replication")));
        Map<String, Boolean> found = new LinkedHashMap<>();
        if ("slave".equals(replication.get("role")) && replication.containsKey("master_host")) {
            String primary = replication.get("master_host").toLowerCase(Locale.ROOT) + ":" + replication.get("master_port");
            found.put(primary, true);
            try {
                Topology.replicas(Topology.fields(text(command(primary, "INFO", "replication")))).forEach(replica -> found.put(replica, false));
            }
            catch (RedisFailure unreachable) {
                if (!unreachable.unreachable()) {
                    throw unreachable;
                }
            }
            found.putIfAbsent(seed, false);
        }
        else {
            found.put(seed, true);
            Topology.replicas(replication).forEach(replica -> found.put(replica, false));
        }
        List<Topology.Node> nodes = new ArrayList<>();
        found.forEach((address, primary) -> nodes.add(new Topology.Node(address, primary)));
        return new Topology(product, version, false, nodes);
    }

    private static String text(@Nullable Object answer)
    {
        return answer == null ? "" : answer.toString();
    }

    // A node's connection stays open for the next command, and closes with the deployment.
    @SuppressWarnings("PMD.CloseResource")
    @Override
    public @Nullable Object command(String node, String... command)
    {
        Link link = nodes.computeIfAbsent(node, address -> new Link(open(address), new ReentrantLock()));
        Jedis connection = link.connection();
        link.lock().lock();
        try {
            try {
                String[] arguments = new String[command.length - 1];
                System.arraycopy(command, 1, arguments, 0, arguments.length);
                ProtocolCommand name = () -> SafeEncoder.encode(command[0]);
                return SafeEncoder.encodeObject(connection.sendCommand(name, arguments));
            }
            catch (JedisConnectionException broken) {
                nodes.remove(node, link);
                connection.close();
                throw new RedisFailure(node + " cannot be reached: " + broken.getMessage(), true, broken);
            }
            catch (JedisException refused) {
                throw new RedisFailure(node + ": " + refused.getMessage(), false, refused);
            }
        }
        finally {
            link.lock().unlock();
        }
    }

    private Jedis open(String node)
    {
        try {
            return connect.apply(node);
        }
        catch (JedisConnectionException unreachable) {
            throw new RedisFailure(node + " cannot be reached: " + unreachable.getMessage(), true, unreachable);
        }
    }

    @Override
    public void close()
    {
        nodes.values().forEach(link -> link.connection().close());
        nodes.clear();
    }
}
