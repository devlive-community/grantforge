// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis;

import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisProviderTest
{
    private static final ServiceConfig CONFIG = new ServiceConfig("cache", Map.of(RedisSettings.NODES, "redis1:6379", RedisSettings.USER, "lookup", RedisSettings.PASSWORD,
            "secret"));

    private static RedisProvider provider(Map<String, Jedis> nodes)
    {
        return new RedisProvider(settings -> new RedisClient(settings)
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
        });
    }

    @Test
    void definesTheLevelsAndAccessTypesInRedissTerms()
    {
        ServiceTypeDefinition definition = new RedisProvider().definition();
        assertThat(definition.name()).isEqualTo("redis");
        assertThat(definition.resources()).extracting(ResourceDefinition::name).containsExactly("key", "channel", "node");
        assertThat(definition.resources().get(0).accessTypes()).containsExactlyInAnyOrder("read", "write");
        assertThat(definition.resources().get(1).accessTypes()).containsExactlyInAnyOrder("subscribe", "publish");
        assertThat(definition.resources().get(2).accessTypes()).containsExactly("monitor");
        assertThat(definition.rowFilter()).isNull();
        assertThat(definition.dataMask()).isNull();
    }

    @Test
    void validatesItsSettings()
    {
        assertThat(new RedisProvider().validateConfig(CONFIG)).isEmpty();
        assertThat(new RedisProvider().validateConfig(new ServiceConfig("cache", Map.of()))).isNotEmpty();
    }

    @Test
    void theConnectionTestDescribesEachKindOfDeployment()
    {
        Map<String, String> server = Map.of("server", "redis_version:7.2.17");
        assertThat(provider(FakeJedis.deployment(Map.of("redis1:6379", Map.of("server", "redis_version:7.2.17", "replication", "role:master")), Map.of(), Map.of()))
                .testConnection(CONFIG).message()).isEqualTo("Redis 7.2.17, a single server, signed in as lookup");
        assertThat(provider(FakeJedis.deployment(Map.of("redis1:6379", Map.of("server", "redis_version:7.2.17", "replication",
                "role:master\nslave0:ip=redis2,port=6379")), Map.of(), Map.of())).testConnection(CONFIG).message())
                .isEqualTo("Redis 7.2.17, a primary with 1 replica, signed in as lookup");
        assertThat(provider(FakeJedis.deployment(Map.of("redis1:6379", Map.of("server", "redis_version:7.2.17", "replication",
                "role:master\nslave0:ip=redis2,port=6379\nslave1:ip=redis3,port=6379")), Map.of(), Map.of())).testConnection(CONFIG).message())
                .isEqualTo("Redis 7.2.17, a primary with 2 replicas, signed in as lookup");
        assertThat(provider(FakeJedis.deployment(Map.of("redis1:6379", Map.of("server", "redis_version:7.2.17\nredis_mode:cluster", "nodes",
                "a redis1:6379@16379 myself,master - 0 0 1 connected")), Map.of(), Map.of())).testConnection(CONFIG).message())
                .isEqualTo("Redis 7.2.17, a cluster of 1 node, signed in as lookup");
        assertThat(server).isNotEmpty();
    }

    @Test
    void theConnectionTestReportsWhatGoesWrong()
    {
        assertThat(new RedisProvider().testConnection(new ServiceConfig("cache", Map.of())).message()).startsWith(RedisSettings.NODES);
        assertThat(provider(Map.of()).testConnection(CONFIG).message()).isEqualTo("no Redis node can be reached: refused");
        ServiceConfig tls = new ServiceConfig("cache", Map.of(RedisSettings.NODES, "redis1:6379", RedisSettings.TLS_MODE, "verify-ca", RedisSettings.CA_FILE, "/no/ca.pem"));
        assertThat(new RedisProvider().testConnection(tls).message()).startsWith("the TLS files cannot be used");
    }

    @Test
    void looksUpThroughTheClient()
    {
        RedisProvider provider = provider(FakeJedis.deployment(Map.of("redis1:6379", Map.of("server", "redis_version:7.2.17")), Map.of("redis1:6379", List.of("sales:1")),
                Map.of()));
        assertThat(provider.lookup(new LookupRequest(CONFIG, "key", "sa", Map.of(), 10))).containsExactly("sales:1");
    }

    @Test
    void aLookupOfWhatCannotBeAskedIsInvalidInput()
    {
        RedisProvider provider = new RedisProvider();
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(CONFIG, "database", "", Map.of(), 10))).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(new ServiceConfig("cache", Map.of()), "key", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
    }
}
