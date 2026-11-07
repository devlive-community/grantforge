// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * What a deployment holds: its nodes, each node's users with their ACLs, and whether each node keeps its ACL in a file
 * that {@code ACL SAVE} writes.
 *
 * @param topology the deployment and its nodes
 * @param users each node's users and their ACLs, by node
 * @param aclFiles the nodes that keep their ACL in a file
 */
record ServerState(Topology topology, Map<String, Map<String, UserAcl>> users, Set<String> aclFiles)
{
    /** Copies everything. */
    ServerState
    {
        users = Map.copyOf(users);
        aclFiles = Set.copyOf(aclFiles);
    }

    /**
     * Reads what the deployment holds.
     *
     * @param redis the deployment
     * @return what it holds
     * @throws RedisFailure when a node cannot be reached or refuses the agent
     */
    // A map per node.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    static ServerState read(Redis redis)
    {
        Topology topology = redis.topology();
        Map<String, Map<String, UserAcl>> users = new HashMap<>();
        Set<String> aclFiles = new TreeSet<>();
        for (String node : topology.addresses()) {
            Map<String, UserAcl> held = new HashMap<>();
            if (redis.command(node, "ACL", "USERS") instanceof List<?> names) {
                for (Object name : names) {
                    held.put(String.valueOf(name), UserAcl.of(redis.command(node, "ACL", "GETUSER", String.valueOf(name))));
                }
            }
            users.put(node, held);
            if (aclFile(redis, node)) {
                aclFiles.add(node);
            }
        }
        return new ServerState(topology, users, aclFiles);
    }

    /** Whether a node keeps its ACL in a file; a node that does not let the agent ask is taken as one that does not. */
    private static boolean aclFile(Redis redis, String node)
    {
        try {
            Object answer = redis.command(node, "CONFIG", "GET", "aclfile");
            return answer instanceof List<?> pair && pair.size() > 1 && !String.valueOf(pair.get(1)).isBlank();
        }
        catch (RedisFailure refused) {
            if (refused.unreachable()) {
                throw refused;
            }
            return false;
        }
    }

    /**
     * Returns the names of the users of any node.
     *
     * @return them, sorted
     */
    SortedSet<String> names()
    {
        SortedSet<String> names = new TreeSet<>();
        users.values().forEach(held -> names.addAll(held.keySet()));
        return names;
    }

    /**
     * Returns a user's ACL on a node.
     *
     * @param node the node
     * @param user the user
     * @return its ACL, or {@code null} when the node has no such user
     */
    @Nullable UserAcl acl(String node, String user)
    {
        Map<String, UserAcl> held = users.get(node);
        return held == null ? null : held.get(user);
    }

    /**
     * Returns whether a user may do anything on some node, so that no selector binds it.
     *
     * @param user the user
     * @return whether it is such a user
     */
    boolean superuser(String user)
    {
        return users.values().stream().map(held -> held.get(user)).anyMatch(acl -> acl != null && acl.superuser());
    }
}
