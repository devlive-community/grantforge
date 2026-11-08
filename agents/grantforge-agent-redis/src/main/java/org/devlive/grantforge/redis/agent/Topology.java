// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The nodes of a deployment and what it is: the nodes of a cluster as {@code CLUSTER NODES} lists them, or a primary
 * with the replicas {@code INFO replication} lists, each by the address it announces.
 *
 * @param product Redis or Valkey
 * @param version the release, such as {@code 7.2.17}
 * @param cluster whether the deployment is a cluster
 * @param nodes the nodes, primaries first
 */
record Topology(String product, String version, boolean cluster, List<Node> nodes)
{
    /**
     * One node.
     *
     * @param address its address as {@code host:port}
     * @param primary whether it holds data of its own rather than a copy
     */
    record Node(String address, boolean primary)
    {
    }

    /** Copies the nodes, primaries first. */
    Topology
    {
        List<Node> sorted = new ArrayList<>(nodes.stream().filter(Node::primary).toList());
        nodes.stream().filter(node -> !node.primary()).forEach(sorted::add);
        nodes = List.copyOf(sorted);
    }

    /**
     * Returns the addresses of the primaries.
     *
     * @return them
     */
    List<String> primaries()
    {
        return nodes.stream().filter(Node::primary).map(Node::address).toList();
    }

    /**
     * Returns the addresses of every node.
     *
     * @return them, primaries first
     */
    List<String> addresses()
    {
        return nodes.stream().map(Node::address).toList();
    }

    /**
     * Reads the fields of an {@code INFO} answer.
     *
     * @param info the answer
     * @return the fields by name; section headers and blank lines are left out
     */
    static Map<String, String> fields(String info)
    {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : info.split("\\r?\\n", -1)) {
            int colon = line.indexOf(':');
            if (colon > 0 && !line.startsWith("#")) {
                fields.put(line.substring(0, colon), line.substring(colon + 1).strip());
            }
        }
        return fields;
    }

    /**
     * Names the server of an {@code INFO server} answer.
     *
     * @param server its fields
     * @return Valkey or Redis
     */
    static String product(Map<String, String> server)
    {
        return server.containsKey("valkey_version") || "valkey".equals(server.get("server_name")) ? "Valkey" : "Redis";
    }

    /**
     * Returns the release of an {@code INFO server} answer.
     *
     * @param server its fields
     * @return such as {@code 7.2.17}, Valkey's own release for Valkey
     */
    static String version(Map<String, String> server)
    {
        return server.getOrDefault("valkey_version", server.getOrDefault("redis_version", "of an unknown release"));
    }

    /**
     * Reads the nodes of a {@code CLUSTER NODES} answer, without those that failed or have no address.
     *
     * @param text the answer
     * @return the nodes
     */
    static List<Node> clusterNodes(String text)
    {
        List<Node> nodes = new ArrayList<>();
        for (String line : text.split("\\r?\\n", -1)) {
            String[] fields = line.strip().split(" ");
            if (fields.length < 3) {
                continue;
            }
            String flags = fields[2];
            if (flags.contains("fail") || flags.contains("noaddr") || flags.contains("handshake")) {
                continue;
            }
            String address = fields[1];
            int bus = address.indexOf('@');
            nodes.add(new Node((bus < 0 ? address : address.substring(0, bus)).toLowerCase(Locale.ROOT), flags.contains("master")));
        }
        return nodes;
    }

    /**
     * Reads the replicas of a primary's {@code INFO replication} answer.
     *
     * @param replication its fields
     * @return the replicas' addresses
     */
    static List<String> replicas(Map<String, String> replication)
    {
        List<String> replicas = new ArrayList<>();
        replication.forEach((name, value) -> {
            if (name.matches("slave\\d+")) {
                Map<String, String> replica = new LinkedHashMap<>();
                for (String pair : value.split(",")) {
                    int equals = pair.indexOf('=');
                    if (equals > 0) {
                        replica.put(pair.substring(0, equals), pair.substring(equals + 1));
                    }
                }
                if (replica.containsKey("ip") && replica.containsKey("port")) {
                    replicas.add(replica.get("ip").toLowerCase(Locale.ROOT) + ":" + replica.get("port"));
                }
            }
        });
        return replicas;
    }
}
