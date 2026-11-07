// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

/** The Redis plugin against a real cluster of three primaries with a replica each: the cluster found from one node, and keys of every primary. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class RedisClusterPluginIT
{
    @TempDir
    static Path files;

    private final RedisProvider provider = new RedisProvider();
    private @Nullable RedisNode cluster;

    @BeforeAll
    void start() throws Exception
    {
        RedisNode started = new RedisNode("redis:8.10.3", true, new TestCertificates(files));
        cluster = started;
        started.everywhere("ACL SETUSER lookup on >lookup-secret -@all +@connection +scan +pubsub|channels +info +cluster|nodes +acl|whoami ~* &*");
        for (int index = 0; index < 30; index++) {
            started.cli(started.ports().get(0), "-c SET order:" + index + " x");
        }
    }

    @AfterAll
    void stop()
    {
        if (cluster != null) {
            cluster.close();
        }
    }

    @Test
    void findsTheClusterFromOneNodeAndLooksInEveryPrimary()
    {
        RedisNode started = requireNonNull(cluster, "cluster");
        ServiceConfig config = new ServiceConfig("cache", Map.of(RedisSettings.NODES, started.addresses().get(3), RedisSettings.USER, "lookup", RedisSettings.PASSWORD,
                "lookup-secret"));
        ConnectionResult result = provider.testConnection(config);
        assertThat(result.message()).isEqualTo("Redis 8.10.3, a cluster of 6 nodes, signed in as lookup");
        assertThat(provider.lookup(new LookupRequest(config, RedisProvider.NODE, "", Map.of(), 50))).containsExactlyInAnyOrderElementsOf(started.addresses());
        List<String> keys = provider.lookup(new LookupRequest(config, RedisProvider.KEY, "order:", Map.of(), 100));
        assertThat(keys).as("keys of every primary").hasSize(30);
    }
}
