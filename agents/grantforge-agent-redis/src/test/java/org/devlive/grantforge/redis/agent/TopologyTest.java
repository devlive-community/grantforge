// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TopologyTest
{
    @Test
    void readsTheFieldsOfAnInfoAnswer()
    {
        Map<String, String> fields = Topology.fields("# Server\r\nredis_version:7.2.17\r\nredis_mode:standalone\r\n\r\nempty:\r\n");
        assertThat(fields).containsEntry("redis_version", "7.2.17").containsEntry("redis_mode", "standalone").containsEntry("empty", "").hasSize(3);
    }

    @Test
    void namesRedisAndValkey()
    {
        assertThat(Topology.product(Map.of("redis_version", "7.2.17"))).isEqualTo("Redis");
        assertThat(Topology.product(Map.of("server_name", "valkey", "redis_version", "7.2.4"))).isEqualTo("Valkey");
        assertThat(Topology.product(Map.of("valkey_version", "9.1.3"))).isEqualTo("Valkey");
        assertThat(Topology.version(Map.of("valkey_version", "9.1.3", "redis_version", "7.2.4"))).isEqualTo("9.1.3");
        assertThat(Topology.version(Map.of("redis_version", "8.10.3"))).isEqualTo("8.10.3");
        assertThat(Topology.version(Map.of())).isEqualTo("of an unknown release");
    }

    @Test
    void readsTheNodesOfACluster()
    {
        String text = String.join("\n",
                "07c3 10.0.0.1:7000@17000 myself,master - 0 0 1 connected 0-5460",
                "67ed 10.0.0.2:7001@17001,host-b master - 0 1 2 connected 5461-10922",
                "292f 10.0.0.3:7002@17002 slave 07c3 0 1 3 connected",
                "aaaa 10.0.0.4:7003@17003 master,fail - 0 1 4 disconnected",
                "bbbb :0@0 master,noaddr - 0 1 5 disconnected",
                "cccc 10.0.0.6:7005@17005 handshake - 0 1 6 disconnected",
                "short line", "");
        assertThat(Topology.clusterNodes(text)).containsExactly(new Topology.Node("10.0.0.1:7000", true), new Topology.Node("10.0.0.2:7001", true),
                new Topology.Node("10.0.0.3:7002", false));
    }

    @Test
    void readsTheReplicasOfAPrimary()
    {
        Map<String, String> replication = Map.of("role", "master", "slave0", "ip=10.0.0.2,port=6380,state=online,offset=1,lag=0", "slave1", "ip=10.0.0.3",
                "slave_read_only", "1");
        assertThat(Topology.replicas(replication)).containsExactly("10.0.0.2:6380");
    }

    @Test
    void putsThePrimariesFirst()
    {
        Topology topology = new Topology("Redis", "7.2.17", false, List.of(new Topology.Node("b:2", false), new Topology.Node("a:1", true)));
        assertThat(topology.addresses()).containsExactly("a:1", "b:2");
        assertThat(topology.primaries()).containsExactly("a:1");
    }
}
