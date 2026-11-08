// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis;

import org.devlive.grantforge.plugin.api.LookupException;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisAccessControlException;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisLookupTest
{
    private static final RedisSettings SETTINGS = new RedisSettings(List.of("redis1:6379"), "lookup", "secret", RedisSettings.DISABLE, null, null, null, null, 1000);

    private static RedisClient client(Map<String, Jedis> nodes)
    {
        return new RedisClient(SETTINGS)
        {
            @Override
            Jedis connect(HostAndPort address)
            {
                return requireNonNull(nodes.get(address.toString()), "node");
            }
        };
    }

    private final Map<String, Jedis> nodes = FakeJedis.deployment(Map.of(
            "redis1:6379", Map.of("server", "redis_version:7.2.17", "replication", "role:master\nslave0:ip=redis2,port=6379"),
            "redis2:6379", Map.of()), Map.of("redis1:6379", List.of("sales:1", "sales:2", "hr:1")), Map.of("redis1:6379", List.of("news:eu"), "redis2:6379",
            List.of("news:us")));

    @Test
    void refusesALevelTheServiceTypeDoesNotHave()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> RedisLookup.of("database", "", 10)).withMessageContaining("database");
        assertThat(RedisLookup.of(RedisProvider.KEY, " sa ", 0)).isEqualTo(new RedisLookup(RedisProvider.KEY, "sa", 1));
    }

    @Test
    void listsNodesKeysOfThePrimariesAndChannelsOfEveryNode()
    {
        assertThat(RedisLookup.of(RedisProvider.NODE, "redis2", 10).run(client(nodes))).containsExactly("redis2:6379");
        assertThat(RedisLookup.of(RedisProvider.KEY, "", 10).run(client(nodes))).containsExactly("hr:1", "sales:1", "sales:2");
        assertThat(RedisLookup.of(RedisProvider.KEY, "", 2).run(client(nodes))).as("at most so many").hasSize(2);
        assertThat(RedisLookup.of(RedisProvider.CHANNEL, "news", 10).run(client(nodes))).containsExactly("news:eu", "news:us");
    }

    @Test
    void scansAtMostSoManyRounds()
    {
        List<String> many = IntStream.range(0, RedisLookup.SCAN_ROUNDS * 2 + 10).mapToObj(index -> "k" + index).toList();
        Map<String, Jedis> big = FakeJedis.deployment(Map.of("redis1:6379", Map.of("server", "redis_version:7.2.17")), Map.of("redis1:6379", many), Map.of());
        assertThat(RedisLookup.of(RedisProvider.KEY, "", 1000).run(client(big))).hasSize(RedisLookup.SCAN_ROUNDS * 2);
    }

    @Test
    void escapesWhatRedisReadsAsWildcards()
    {
        assertThat(RedisLookup.escape("a*b?c[d]e\\f")).isEqualTo("a\\*b\\?c\\[d\\]e\\\\f");
        assertThat(RedisLookup.escape("plain:key")).isEqualTo("plain:key");
    }

    @Test
    void tellsRefusedSignInsFromMissingPermissionsAndUnreachableNodes()
    {
        assertThat(RedisLookup.failure(new JedisAccessControlException("WRONGPASS invalid username-password pair")).getReason())
                .isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(RedisLookup.failure(new JedisDataException("NOAUTH Authentication required.")).getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(RedisLookup.failure(new JedisAccessControlException("NOPERM User lookup has no permissions to run the 'scan' command")).getReason())
                .isEqualTo(LookupException.Reason.ACCESS_DENIED);
        assertThat(RedisLookup.failure(new JedisConnectionException("refused")).getReason()).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(RedisLookup.failure(new JedisDataException("ERR odd")).getMessage()).isEqualTo("Redis failed the lookup: ERR odd");
    }

    @Test
    void aFailingDeploymentFailsTheLookupWithItsReason()
    {
        RedisClient failing = new RedisClient(SETTINGS)
        {
            @Override
            Jedis connect(HostAndPort address)
            {
                throw new JedisConnectionException("refused");
            }
        };
        assertThatThrownBy(() -> RedisLookup.of(RedisProvider.KEY, "", 1).run(failing)).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
        assertThat(new JedisException("x")).isNotNull();
    }
}
