// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The agent against a real sharded cluster, a config server, a shard and a {@code mongos}: it keeps its roles through the
 * {@code mongos}, what it grants takes effect there once the router's user cache has expired, the deployment goes by the name
 * policies know it by, and it says a {@code mongos} has no profiler.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class MongoDbShardedAgentIT
{
    private static final String IMAGE = "mongo:9.0.4";

    @TempDir
    static Path files;

    private final List<GenericContainer<?>> containers = new ArrayList<>();
    private final Network network = Network.newNetwork();
    private int port;
    private @Nullable GenericContainer<?> router;
    private @Nullable AgentServer server;
    private @Nullable MongoDbAgent agent;

    @SuppressWarnings("resource")
    private GenericContainer<?> member(String alias, String command) throws Exception
    {
        byte[] secret = new byte[64];
        ThreadLocalRandom.current().nextBytes(secret);
        Path key = files.resolve("key");
        if (!Files.exists(key)) {
            Files.writeString(key, Base64.getEncoder().encodeToString(secret));
        }
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(IMAGE)).withNetwork(network).withNetworkAliases(alias)
                .withCreateContainerCmdModifier(cmd -> cmd.withUser("0").withEntrypoint("bash"))
                .withCopyFileToContainer(MountableFile.forHostPath(key, 0644), "/tmp/key.source")
                .withCommand("-c", "cp /tmp/key.source /tmp/key; chmod 400 /tmp/key; mkdir -p /data/db; exec " + command)
                .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1).withStartupTimeout(Duration.ofMinutes(3)));
        containers.add(container);
        return container;
    }

    private static String shell(GenericContainer<?> container, int on, String script) throws Exception
    {
        Container.ExecResult result = container.execInContainer("mongosh", "--quiet", "--port", Integer.toString(on), "--eval", script);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("mongosh failed: " + result.getStdout() + result.getStderr());
        }
        return result.getStdout();
    }

    @BeforeAll
    void start() throws Exception
    {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        GenericContainer<?> config = member("config", "mongod --configsvr --replSet cfg --keyFile /tmp/key --bind_ip_all --port 27019 --dbpath /data/db");
        config.start();
        shell(config, 27019, "rs.initiate({_id: 'cfg', configsvr: true, members: [{_id: 0, host: 'config:27019'}]})");
        GenericContainer<?> shard = member("shard", "mongod --shardsvr --replSet shard1 --keyFile /tmp/key --bind_ip_all --port 27018 --dbpath /data/db");
        shard.start();
        shell(shard, 27018, "rs.initiate({_id: 'shard1', members: [{_id: 0, host: 'shard:27018'}]})");
        GenericContainer<?> mongos = member("router", "mongos --configdb cfg/config:27019 --keyFile /tmp/key --bind_ip_all --port " + port
                + " --setParameter userCacheInvalidationIntervalSecs=1");
        mongos.withCreateContainerCmdModifier(cmd -> {
            cmd.withUser("0").withEntrypoint("bash");
            HostConfig host = cmd.getHostConfig();
            if (host != null) {
                host.withPortBindings(new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", port), new ExposedPort(port)));
            }
            cmd.withExposedPorts(new ExposedPort(port));
        });
        mongos.start();
        router = mongos;
        // The first user comes through the localhost exception of the mongos.
        shell(mongos, port, "db.getSiblingDB('admin').createUser({user: 'root', pwd: 'root-secret', roles: ['root']})");
        Container.ExecResult setUp = mongos.execInContainer("mongosh", "--quiet", "--port", Integer.toString(port), "-u", "root", "-p", "root-secret", "--authenticationDatabase", "admin",
                "--eval", """
                        for (let tries = 0; tries < 100; tries++) {
                            try { sh.addShard('shard1/shard:27018'); break; } catch (e) { sleep(300); }
                        }
                        const admin = db.getSiblingDB('admin');
                        const sales = db.getSiblingDB('sales');
                        sales.orders.insertOne({_id: 1});
                        sales.items.insertOne({_id: 1});
                        admin.createRole({role: 'inventory', privileges: [{resource: {db: '', collection: ''}, actions: ['listCollections']}], roles: []});
                        admin.createUser({user: 'grantforge', pwd: 'agent-secret', roles: ['userAdminAnyDatabase', 'clusterMonitor', 'inventory']});
                        admin.createUser({user: 'alice', pwd: 'alice-secret', roles: []});
                        """);
        assertThat(setUp.getExitCode()).as(setUp.getStdout() + setUp.getStderr()).isZero();
        AgentServer running = new AgentServer(new MongoSnapshots());
        server = running;
        running.publish(new MongoSnapshots().allow(List.of("alice"), List.of("read"), "database", "sales", "collection", "orders")
                .allow(List.of("alice"), List.of("list_databases"), "deployment", "sharded"));
        Map<String, String> values = new HashMap<>(Map.of("grantforge.server.url", "http://127.0.0.1:" + running.port(),
                "grantforge.token.file", Files.writeString(files.resolve("token"), AgentServer.TOKEN).toString(), "grantforge.instance", "mongodb-sharded",
                "grantforge.cache.dir", files.resolve("cache").toString(), "grantforge.refresh.interval.ms", "1000", "grantforge.mongodb.uri", "mongodb://127.0.0.1:" + port + "/",
                "grantforge.mongodb.user", "grantforge", "grantforge.mongodb.password.file", Files.writeString(files.resolve("password"), "agent-secret").toString()));
        values.putAll(Map.of("grantforge.sync.interval.ms", "3600000", "grantforge.audit.log.interval.ms", "600000"));
        agent = MongoDbAgent.start(AgentConfig.parse(values, "test"));
        eventually("the agent takes the policies", () -> {
            Snapshot snapshot = agent().agent().snapshot();
            return snapshot != null && snapshot.policyVersion() == 2;
        });
    }

    @AfterAll
    void stop()
    {
        if (agent != null) {
            agent.close();
        }
        if (server != null) {
            server.close();
        }
        containers.forEach(GenericContainer::stop);
        network.close();
    }

    private MongoDbAgent agent()
    {
        return requireNonNull(agent, "agent");
    }

    private static void eventually(String what, BooleanSupplier condition) throws InterruptedException
    {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("timed out waiting until " + what);
            }
            Thread.sleep(200);
        }
    }

    private boolean aliceReads(String collection)
    {
        MongoClientSettings settings = MongoClientSettings.builder().applyConnectionString(new ConnectionString("mongodb://127.0.0.1:" + port + "/"))
                .credential(MongoCredential.createScramSha256Credential("alice", "admin", "alice-secret".toCharArray())).build();
        try (MongoClient client = MongoClients.create(settings)) {
            client.getDatabase("sales").getCollection(collection).find().first();
            return true;
        }
        catch (MongoCommandException refused) {
            if (refused.getErrorCode() == 13) {
                return false;
            }
            throw refused;
        }
    }

    @Test
    void keepsItsRolesThroughTheRouter() throws Exception
    {
        assertThat(requireNonNull(agent().syncNow(), "outcome").created()).isEqualTo(1);
        eventually("the router takes the role", () -> aliceReads("orders"));
        assertThat(aliceReads("items")).isFalse();
        assertThat(agent().readAccessesNow()).as("a mongos has no profiler").isZero();
        assertThat(requireNonNull(agent().syncNow(), "outcome").changed()).as("the deployment is known as policies name it").isFalse();
    }
}
