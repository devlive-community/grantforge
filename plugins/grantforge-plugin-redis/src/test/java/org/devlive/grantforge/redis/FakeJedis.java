// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A Jedis connection of a node that answers from tables: its INFO sections, its keys and its channels. */
final class FakeJedis
{
    private FakeJedis()
    {
    }

    /** The nodes of a fake deployment by address, each made when a client connects to it. */
    static Map<String, Jedis> deployment(Map<String, Map<String, String>> infos, Map<String, List<String>> keys, Map<String, List<String>> channels)
    {
        Map<String, Jedis> nodes = new HashMap<>();
        infos.forEach((address, info) -> nodes.put(address, node(info, keys.getOrDefault(address, List.of()), channels.getOrDefault(address, List.of()))));
        return nodes;
    }

    static Jedis node(Map<String, String> info, List<String> keys, List<String> channels)
    {
        Jedis node = mock(Jedis.class);
        when(node.info(anyString())).thenAnswer(call -> info.getOrDefault(call.getArgument(0, String.class), ""));
        when(node.clusterNodes()).thenAnswer(call -> info.getOrDefault("nodes", ""));
        when(node.aclWhoAmI()).thenReturn("lookup");
        when(node.pubsubChannels(anyString())).thenReturn(channels);
        when(node.scan(anyString(), any(ScanParams.class))).thenAnswer(call -> {
            String cursor = call.getArgument(0, String.class);
            int start = Integer.parseInt(cursor);
            int end = Math.min(keys.size(), start + 2);
            return new ScanResult<>(end >= keys.size() ? ScanParams.SCAN_POINTER_START : Integer.toString(end), keys.subList(start, end));
        });
        return node;
    }
}
