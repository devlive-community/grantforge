// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.util.SafeEncoder;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JedisRedisTest
{
    private static AgentConfig.Connection settings(List<String> nodes, boolean discover)
    {
        return new AgentConfig.Connection(nodes, discover, "grantforge", "secret", AgentConfig.DISABLE, null, null, null, null, Duration.ofSeconds(7));
    }

    /** A node that answers the first word of a command from a table, as Redis sends bytes. */
    private static Jedis node(Map<String, Object> answers)
    {
        Jedis node = mock(Jedis.class);
        when(node.sendCommand(any(ProtocolCommand.class), any(String[].class))).thenAnswer(call -> {
            ProtocolCommand command = call.getArgument(0);
            String name = SafeEncoder.encode(command.getRaw());
            Object[] all = call.getArguments();
            String first = all.length > 1 && all[1] instanceof String text ? text : "";
            Object answer = answers.getOrDefault(name + " " + first, answers.get(name));
            if (answer instanceof RuntimeException failure) {
                throw failure;
            }
            return answer instanceof String text ? SafeEncoder.encode(text) : answer;
        });
        return node;
    }

    private static Function<String, Jedis> nodes(Map<String, Jedis> nodes)
    {
        return address -> {
            Jedis node = nodes.get(address);
            if (node == null) {
                throw new JedisConnectionException("refused");
            }
            return node;
        };
    }

    @Test
    void signsInWithTheUserPasswordAndTimeouts(@TempDir Path files) throws Exception
    {
        JedisClientConfig config = JedisRedis.config(settings(List.of("p:6379"), true));
        assertThat(config.getUser()).isEqualTo("grantforge");
        assertThat(config.getPassword()).isEqualTo("secret");
        assertThat(config.getSocketTimeoutMillis()).isEqualTo(7000);
        assertThat(config.isSsl()).isFalse();
        TestCertificates certificates = new TestCertificates(files);
        AgentConfig.Connection full = new AgentConfig.Connection(List.of("p:6379"), true, null, null, AgentConfig.VERIFY_FULL, certificates.file("ca.pem"),
                certificates.file("client-pkcs8.pem"), null, null, Duration.ofSeconds(7));
        assertThat(JedisRedis.config(full).getSslParameters().getEndpointIdentificationAlgorithm()).isEqualTo("HTTPS");
        AgentConfig.Connection ca = new AgentConfig.Connection(List.of("p:6379"), true, null, null, AgentConfig.VERIFY_CA, null, null, null, null, Duration.ofSeconds(7));
        assertThat(JedisRedis.config(ca).getSslParameters().getEndpointIdentificationAlgorithm()).isNull();
        AgentConfig.Connection missing = new AgentConfig.Connection(List.of("p:6379"), true, null, null, AgentConfig.VERIFY_CA, "/no/ca.pem", null, null, null,
                Duration.ofSeconds(7));
        assertThatThrownBy(() -> JedisRedis.config(missing)).isInstanceOf(RedisFailure.class).hasMessageStartingWith("the TLS files cannot be used");
    }

    @Test
    void findsAPrimaryAndItsReplicasFromTheFirstNodeThatAnswers()
    {
        Map<String, Jedis> nodes = new HashMap<>();
        nodes.put("p:6379", node(Map.of("INFO server", "redis_version:7.2.17", "INFO replication", "role:master\nslave0:ip=R,port=6379")));
        try (JedisRedis redis = new JedisRedis(settings(List.of("down:1", "p:6379"), true), nodes(nodes))) {
            assertThat(redis.topology().nodes()).containsExactly(new Topology.Node("p:6379", true), new Topology.Node("r:6379", false));
        }
    }

    @Test
    void findsThePrimaryFromAReplicaEvenWhenThePrimaryIsDown()
    {
        Map<String, Jedis> nodes = new HashMap<>();
        nodes.put("r:6379", node(Map.of("INFO server", "redis_version:7.2.17", "INFO replication", "role:slave\nmaster_host:P\nmaster_port:6379")));
        try (JedisRedis redis = new JedisRedis(settings(List.of("r:6379"), true), nodes(nodes))) {
            assertThat(redis.topology().nodes()).containsExactly(new Topology.Node("p:6379", true), new Topology.Node("r:6379", false));
        }
        nodes.put("p:6379", node(Map.of("INFO replication", "role:master\nslave0:ip=r,port=6379\nslave1:ip=r2,port=6379")));
        try (JedisRedis redis = new JedisRedis(settings(List.of("r:6379"), true), nodes(nodes))) {
            assertThat(redis.topology().addresses()).containsExactly("p:6379", "r:6379", "r2:6379");
        }
    }

    @Test
    void findsTheNodesOfAClusterOrTakesThoseGiven()
    {
        Map<String, Jedis> nodes = new HashMap<>();
        nodes.put("a:7000", node(Map.of("INFO server", "redis_version:8.10.3\nredis_mode:cluster", "CLUSTER NODES",
                "x a:7000@17000 myself,master - 0 0 1 connected\ny b:7000@17000 slave x 0 0 1 connected")));
        try (JedisRedis redis = new JedisRedis(settings(List.of("a:7000"), true), nodes(nodes))) {
            assertThat(redis.topology().addresses()).containsExactly("a:7000", "b:7000");
        }
        try (JedisRedis redis = new JedisRedis(settings(List.of("a:7000", "c:7000"), false), nodes(nodes))) {
            assertThat(redis.topology().nodes()).as("the nodes given, a primary unless found otherwise")
                    .containsExactly(new Topology.Node("a:7000", true), new Topology.Node("c:7000", true));
        }
    }

    @Test
    void reportsWhenNoNodeAnswersOrOneRefuses()
    {
        try (JedisRedis redis = new JedisRedis(settings(List.of("down:1"), true), nodes(Map.of()))) {
            assertThatThrownBy(redis::topology).isInstanceOfSatisfying(RedisFailure.class, failed -> assertThat(failed.unreachable()).isTrue());
        }
        try (JedisRedis redis = new JedisRedis(settings(List.of(), true), nodes(Map.of()))) {
            assertThatThrownBy(redis::topology).isInstanceOf(RedisFailure.class).hasMessage("no node is given");
        }
        Map<String, Jedis> refusing = Map.of("p:6379", node(Map.of("INFO", new JedisDataException("NOPERM no"))));
        try (JedisRedis redis = new JedisRedis(settings(List.of("p:6379"), true), nodes(refusing))) {
            assertThatThrownBy(redis::topology).isInstanceOfSatisfying(RedisFailure.class, failed -> assertThat(failed.unreachable()).isFalse());
        }
    }

    @Test
    void sendsCommandsAndDecodesTheAnswers()
    {
        Jedis primary = node(Map.of("ACL USERS", List.of(SafeEncoder.encode("alice")), "PING", "PONG"));
        try (JedisRedis redis = new JedisRedis(settings(List.of("p:6379"), true), nodes(Map.of("p:6379", primary)))) {
            assertThat(redis.command("p:6379", "ACL", "USERS")).isEqualTo(List.of("alice"));
            assertThat(redis.command("p:6379", "PING")).isEqualTo("PONG");
        }
        verify(primary).close();
    }

    @Test
    void reopensANodeWhoseConnectionBroke()
    {
        Map<String, Jedis> nodes = new HashMap<>();
        nodes.put("p:6379", node(Map.of("PING", new JedisConnectionException("broken"))));
        try (JedisRedis redis = new JedisRedis(settings(List.of("p:6379"), true), nodes(nodes))) {
            assertThatThrownBy(() -> redis.command("p:6379", "PING")).isInstanceOfSatisfying(RedisFailure.class, failed -> assertThat(failed.unreachable()).isTrue());
            nodes.put("p:6379", node(Map.of("PING", "PONG")));
            assertThat(redis.command("p:6379", "PING")).isEqualTo("PONG");
            assertThatThrownBy(() -> redis.command("down:1", "PING")).isInstanceOfSatisfying(RedisFailure.class, failed -> assertThat(failed.unreachable()).isTrue());
        }
    }

    @Test
    void connectsToNodesItWasNotGiven()
    {
        JedisRedis redis = new JedisRedis(settings(List.of("127.0.0.1:1"), true));
        assertThatThrownBy(() -> redis.command("127.0.0.1:1", "PING")).isInstanceOfSatisfying(RedisFailure.class, failed -> assertThat(failed.unreachable()).isTrue());
        redis.close();
    }
}
