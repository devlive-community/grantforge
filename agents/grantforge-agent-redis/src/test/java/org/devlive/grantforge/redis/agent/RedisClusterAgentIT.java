// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.exceptions.JedisException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The agent against a real cluster of three primaries with a replica each: found from one node, every node holding the
 * user's selectors, as Redis keeps an ACL on each, so that a cluster client reads keys of every primary.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class RedisClusterAgentIT
{
    @TempDir
    static Path files;

    private @Nullable RedisNode cluster;
    private @Nullable AgentServer server;
    private @Nullable RedisAgent agent;

    @BeforeAll
    void start() throws Exception
    {
        RedisNode started = new RedisNode("redis:8.10.3", true, new TestCertificates(files));
        cluster = started;
        started.everywhere("ACL SETUSER grantforge on >agent-secret -@all +@connection +acl|whoami +acl|users +acl|getuser +acl|setuser +acl|log +acl|dryrun"
                + " +acl|save +config|get +info +cluster|nodes +command|info");
        started.everywhere("ACL SETUSER alice on >alice-secret -@all +@connection +cluster|slots +cluster|shards +cluster|nodes");
        for (int index = 0; index < 30; index++) {
            started.cli(started.ports().get(0), "-c SET order:" + index + " x");
        }
        AgentServer running = new AgentServer(new RedisSnapshots());
        server = running;
        running.publish(new RedisSnapshots().allow(List.of("alice"), List.of("read"), "key", "order:*"));
        Map<String, String> values = new HashMap<>(Map.of("grantforge.server.url", "http://127.0.0.1:" + running.port(),
                "grantforge.token.file", Files.writeString(files.resolve("token"), AgentServer.TOKEN).toString(), "grantforge.instance", "redis-cluster",
                "grantforge.cache.dir", files.resolve("cache").toString(), "grantforge.refresh.interval.ms", "1000",
                "grantforge.redis.nodes", started.addresses().get(4), "grantforge.redis.user", "grantforge",
                "grantforge.redis.password.file", Files.writeString(files.resolve("password"), "agent-secret").toString()));
        values.putAll(Map.of("grantforge.sync.interval.ms", "3600000", "grantforge.audit.log.interval.ms", "600000"));
        RedisAgent made = RedisAgent.start(AgentConfig.parse(values, "test"));
        agent = made;
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (made.agent().snapshot() == null || requireNonNull(made.agent().snapshot(), "snapshot").policyVersion() != 2) {
            assertThat(Instant.now()).isBefore(deadline);
            Thread.sleep(100);
        }
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
        if (cluster != null) {
            cluster.close();
        }
    }

    @Test
    void keepsTheSelectorsOnEveryNodeOfTheCluster() throws Exception
    {
        RedisNode started = requireNonNull(cluster, "cluster");
        Reconciler.Outcome outcome = requireNonNull(requireNonNull(agent, "agent").syncNow(), "outcome");
        assertThat(outcome.replaced()).as("primaries and replicas alike").isEqualTo(6);
        for (int port : started.ports()) {
            assertThat(started.cli(port, "ACL GETUSER alice")).contains("%R~order:*");
        }
        Set<HostAndPort> nodes = Set.of(HostAndPort.from(started.addresses().get(0)));
        try (JedisCluster client = new JedisCluster(nodes, DefaultJedisClientConfig.builder().user("alice").password("alice-secret").build())) {
            for (int index = 0; index < 30; index++) {
                assertThat(client.get("order:" + index)).isEqualTo("x");
            }
            assertThatThrownBy(() -> client.set("order:0", "y")).isInstanceOf(JedisException.class).hasMessageContaining("NOPERM");
        }
        assertThat(requireNonNull(agent, "agent").readLogNow()).isGreaterThanOrEqualTo(0);
    }
}
