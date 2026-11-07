// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.ServiceTypeProvider;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ConfigFieldType;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.devlive.grantforge.plugin.api.model.MatcherType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

import java.util.List;
import java.util.function.Function;

/**
 * The Redis service type in Redis's own terms, for Redis and Valkey alike. Keys decide reading and writing, as Redis's
 * ACL grants keys for reading and for writing by pattern; channels decide subscribing and publishing; each node decides
 * whether its state may be monitored, as Redis keeps an ACL of its own on every node. Patterns compare case-sensitively,
 * as Redis compares them. Redis filters nothing within a value and masks nothing, so the service type offers neither.
 */
public final class RedisProvider
        implements ServiceTypeProvider
{
    static final String TYPE = "redis";
    static final String NODE = "node";
    static final String KEY = "key";
    static final String CHANNEL = "channel";

    static final String READ = "read";
    static final String WRITE = "write";
    static final String SUBSCRIBE = "subscribe";
    static final String PUBLISH = "publish";
    static final String MONITOR = "monitor";

    private final Function<RedisSettings, RedisClient> clients;

    /** Creates the provider the plugin host loads. */
    public RedisProvider()
    {
        this(RedisClient::new);
    }

    RedisProvider(Function<RedisSettings, RedisClient> clients)
    {
        this.clients = clients;
    }

    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder(TYPE).label("Redis").description("Keys and channels of Redis and Valkey servers, replicas and clusters")
                .resources(ResourceDefinition.builder(KEY).label("Key").matcher(MatcherType.WILDCARD).caseSensitive(true).excludesSupported(true)
                                .lookupSupported(true).accessTypes(READ, WRITE).build(),
                        ResourceDefinition.builder(CHANNEL).label("Channel").matcher(MatcherType.WILDCARD).caseSensitive(true).excludesSupported(true)
                                .lookupSupported(true).accessTypes(SUBSCRIBE, PUBLISH).build(),
                        ResourceDefinition.builder(NODE).label("Node").matcher(MatcherType.WILDCARD).caseSensitive(false).excludesSupported(true)
                                .lookupSupported(true).accessTypes(MONITOR).build())
                .accessTypes(AccessTypeDefinition.of(READ, "Read"), AccessTypeDefinition.of(WRITE, "Write and delete"),
                        AccessTypeDefinition.of(SUBSCRIBE, "Subscribe"), AccessTypeDefinition.of(PUBLISH, "Publish"),
                        AccessTypeDefinition.of(MONITOR, "Monitor the node"))
                .configFields(
                        ConfigField.builder(RedisSettings.NODES).label("Nodes").type(ConfigFieldType.STRING).mandatory()
                                .description("A node's address, such as redis1.example.com:6379; several separated by commas. The rest of a cluster and a"
                                        + " primary's replicas are found from them").build(),
                        ConfigField.builder(RedisSettings.USER).label("Username").type(ConfigFieldType.STRING)
                                .description("The lookup user, an ACL user; empty signs in as the default user").build(),
                        ConfigField.builder(RedisSettings.PASSWORD).label("Password").type(ConfigFieldType.SECRET)
                                .description("The lookup user's password").build(),
                        ConfigField.builder(RedisSettings.TLS_MODE).label("TLS mode").type(ConfigFieldType.ENUM)
                                .options(RedisSettings.TLS_MODES.toArray(new String[0])).defaultValue(RedisSettings.DISABLE)
                                .description("disable, verify-ca (check the servers' certificates) or verify-full (also their names)").build(),
                        ConfigField.builder(RedisSettings.CA_FILE).label("CA file").type(ConfigFieldType.STRING)
                                .description("Path on the GrantForge server of the PEM CA certificates the servers are checked against; empty uses the JVM's")
                                .build(),
                        ConfigField.builder(RedisSettings.CERTIFICATE_FILE).label("Client certificate").type(ConfigFieldType.STRING)
                                .description("For servers that ask for client certificates: path on the GrantForge server of the PEM certificate, which may"
                                        + " hold its key too").build(),
                        ConfigField.builder(RedisSettings.KEY_FILE).label("Client key").type(ConfigFieldType.STRING)
                                .description("Path on the GrantForge server of the certificate's PEM key, when its own file").build(),
                        ConfigField.builder(RedisSettings.KEY_PASSWORD).label("Key password").type(ConfigFieldType.SECRET)
                                .description("The password of an encrypted key").build(),
                        ConfigField.builder(RedisSettings.TIMEOUT).label("Timeout (ms)").type(ConfigFieldType.INTEGER)
                                .defaultValue(Integer.toString(RedisSettings.DEFAULT_TIMEOUT)).description("Between 1 and 600000").build())
                .build();
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        return RedisSettings.problems(config);
    }

    /** Signs in and describes the deployment: Redis or Valkey and its release, whether it is a cluster, its nodes and who signed in. */
    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        RedisSettings settings;
        try {
            settings = RedisSettings.of(config);
        }
        catch (IllegalArgumentException invalid) {
            return ConnectionResult.failed(String.valueOf(invalid.getMessage()));
        }
        try {
            RedisClient client = clients.apply(settings);
            Topology topology = client.topology();
            String user = client.on(topology.addresses().get(0), Jedis::aclWhoAmI);
            int nodes = topology.nodes().size();
            String shape = topology.cluster() ? "a cluster of " + nodes + (nodes == 1 ? " node" : " nodes")
                    : nodes == 1 ? "a single server" : "a primary with " + (nodes - 1) + (nodes == 2 ? " replica" : " replicas");
            return new ConnectionResult(ConnectionResult.Status.SUCCEEDED, topology.product() + " " + topology.version() + ", " + shape + ", signed in as " + user);
        }
        catch (JedisException failed) {
            return ConnectionResult.failed(String.valueOf(RedisLookup.failure(failed).getMessage()));
        }
        catch (LookupException failed) {
            return ConnectionResult.failed(String.valueOf(failed.getMessage()));
        }
    }

    /** Lists the nodes, keys or channels that start with what was typed. */
    @Override
    public List<String> lookup(LookupRequest request)
    {
        RedisSettings settings;
        RedisLookup lookup;
        try {
            settings = RedisSettings.of(request.config());
            lookup = RedisLookup.of(request.resource(), request.userInput(), request.limit());
        }
        catch (IllegalArgumentException invalid) {
            throw new LookupException(LookupException.Reason.INVALID_INPUT, String.valueOf(invalid.getMessage()), invalid);
        }
        return lookup.run(clients.apply(settings));
    }
}
