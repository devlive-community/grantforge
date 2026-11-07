// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis;

import org.devlive.grantforge.plugin.api.LookupException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

class RedisClientTest
{
    private static RedisSettings settings(List<String> nodes)
    {
        return new RedisSettings(nodes, "lookup", "secret", RedisSettings.DISABLE, null, null, null, null, 7000);
    }

    private static RedisClient client(RedisSettings settings, Map<String, Jedis> nodes)
    {
        return new RedisClient(settings)
        {
            @Override
            Jedis connect(HostAndPort address)
            {
                Jedis node = nodes.get(address.toString());
                if (node == null) {
                    throw new JedisConnectionException("refused");
                }
                return node;
            }
        };
    }

    @Test
    void signsInWithTheUserPasswordAndTimeouts()
    {
        JedisClientConfig config = new RedisClient(settings(List.of("redis1:6379"))).config();
        assertThat(config.getUser()).isEqualTo("lookup");
        assertThat(config.getPassword()).isEqualTo("secret");
        assertThat(config.getConnectionTimeoutMillis()).isEqualTo(7000);
        assertThat(config.getSocketTimeoutMillis()).isEqualTo(7000);
        assertThat(config.isSsl()).isFalse();
    }

    @Test
    void checksNamesInVerifyFullModeOnly(@TempDir Path files) throws Exception
    {
        TestCertificates certificates = new TestCertificates(files);
        RedisSettings full = new RedisSettings(List.of("redis1:6379"), null, null, RedisSettings.VERIFY_FULL, certificates.file("ca.pem"), certificates.file("client.pem"),
                certificates.file("client-key-only.pem"), null, 7000);
        JedisClientConfig config = new RedisClient(full).config();
        assertThat(config.isSsl()).isTrue();
        assertThat(config.getSslParameters().getEndpointIdentificationAlgorithm()).isEqualTo("HTTPS");
        RedisSettings ca = new RedisSettings(List.of("redis1:6379"), null, null, RedisSettings.VERIFY_CA, null, null, null, null, 7000);
        assertThat(new RedisClient(ca).config().getSslParameters().getEndpointIdentificationAlgorithm()).isNull();
    }

    @Test
    void reportsTlsFilesThatCannotBeUsed()
    {
        RedisSettings missing = new RedisSettings(List.of("redis1:6379"), null, null, RedisSettings.VERIFY_CA, "/no/such/ca.pem", null, null, null, 7000);
        assertThatThrownBy(() -> new RedisClient(missing).config()).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getMessage()).startsWith("the TLS files cannot be used"));
    }

    @Test
    void findsAPrimaryAndItsReplicas()
    {
        Map<String, Jedis> nodes = FakeJedis.deployment(Map.of("redis1:6379", Map.of("server", "redis_version:7.2.17\nredis_mode:standalone",
                "replication", "role:master\nslave0:ip=redis2,port=6379,state=online")), Map.of(), Map.of());
        Topology topology = client(settings(List.of("down:1", "redis1:6379")), nodes).topology();
        assertThat(topology.cluster()).isFalse();
        assertThat(topology.product()).isEqualTo("Redis");
        assertThat(topology.nodes()).containsExactly(new Topology.Node("redis1:6379", true), new Topology.Node("redis2:6379", false));
        verify(nodes.get("redis1:6379")).close();
    }

    @Test
    void findsThePrimaryFromAReplica()
    {
        Map<String, Jedis> nodes = FakeJedis.deployment(Map.of(
                "redis2:6379", Map.of("server", "redis_version:7.2.17", "replication", "role:slave\nmaster_host:redis1\nmaster_port:6379"),
                "redis1:6379", Map.of("replication", "role:master\nslave0:ip=redis2,port=6379\nslave1:ip=redis3,port=6379")), Map.of(), Map.of());
        assertThat(client(settings(List.of("redis2:6379")), nodes).topology().addresses()).containsExactly("redis1:6379", "redis2:6379", "redis3:6379");
        Map<String, Jedis> lonely = FakeJedis.deployment(Map.of(
                "redis2:6379", Map.of("server", "redis_version:7.2.17", "replication", "role:slave\nmaster_host:redis1\nmaster_port:6379")), Map.of(), Map.of());
        assertThat(client(settings(List.of("redis2:6379")), lonely).topology().nodes()).as("a primary that cannot be reached")
                .containsExactly(new Topology.Node("redis1:6379", true), new Topology.Node("redis2:6379", false));
    }

    @Test
    void findsTheNodesOfACluster()
    {
        Map<String, Jedis> nodes = FakeJedis.deployment(Map.of("redis1:7000", Map.of("server", "valkey_version:9.1.3\nserver_name:valkey\nserver_mode:cluster",
                "nodes", "a 10.0.0.1:7000@17000 myself,master - 0 0 1 connected\nb 10.0.0.2:7000@17000 slave a 0 0 1 connected")), Map.of(), Map.of());
        Topology topology = client(settings(List.of("redis1:7000")), nodes).topology();
        assertThat(topology.cluster()).isTrue();
        assertThat(topology.product()).isEqualTo("Valkey");
        assertThat(topology.addresses()).containsExactly("10.0.0.1:7000", "10.0.0.2:7000");
    }

    @Test
    void reportsWhenNoNodeAnswers()
    {
        assertThatThrownBy(() -> client(settings(List.of("down:1", "down:2")), Map.of()).topology()).isInstanceOf(JedisConnectionException.class).hasMessage("refused");
        assertThatThrownBy(() -> client(settings(List.of()), Map.of()).topology()).isInstanceOf(JedisConnectionException.class);
    }

    @Test
    void connectsToTheNodeAtOnce()
    {
        RedisClient client = new RedisClient(settings(List.of("127.0.0.1:1")));
        assertThatThrownBy(() -> client.connect(HostAndPort.from("127.0.0.1:1"))).isInstanceOf(JedisConnectionException.class);
        assertThatThrownBy(() -> client.on("127.0.0.1:1", Jedis::ping)).isInstanceOf(JedisConnectionException.class);
    }
}
