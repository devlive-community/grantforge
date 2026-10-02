// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The inheritance graph of a tenant's roles: which roles each role inherits from, directly and through its parents.
 * The graph has no cycles; {@link #wouldCycle} tells whether a new link would make one.
 */
public final class RoleHierarchy
{
    private final Map<Long, List<Long>> parents;
    private final Map<Long, List<Long>> children;

    /**
     * Builds the graph.
     *
     * @param links every link of the tenant
     */
    public RoleHierarchy(Collection<RoleParent> links)
    {
        this.parents = links.stream().collect(Collectors.groupingBy(RoleParent::getRoleId,
                Collectors.mapping(RoleParent::getParentId, Collectors.toList())));
        this.children = links.stream().collect(Collectors.groupingBy(RoleParent::getParentId,
                Collectors.mapping(RoleParent::getRoleId, Collectors.toList())));
    }

    /**
     * Returns the roles a role inherits from directly.
     *
     * @param roleId the role
     * @return its parents
     */
    public List<Long> parentsOf(long roleId)
    {
        return List.copyOf(parents.getOrDefault(roleId, List.of()));
    }

    /**
     * Returns the roles a role inherits from, each with how many links away it is (1 for a parent), nearest first.
     *
     * @param roleId the role
     * @return the ancestors and their distance; the role itself is not included
     */
    public Map<Long, Integer> ancestors(long roleId)
    {
        return walk(roleId, parents);
    }

    /**
     * Returns the roles that inherit from a role, each with its distance, nearest first.
     *
     * @param roleId the role
     * @return the descendants and their distance; the role itself is not included
     */
    public Map<Long, Integer> descendants(long roleId)
    {
        return walk(roleId, children);
    }

    /**
     * Returns whether letting a role inherit from a parent would close a cycle: the parent is the role or inherits
     * from it already.
     *
     * @param roleId the inheriting role
     * @param parentId the role to inherit from
     * @return {@code true} if the link is not allowed
     */
    public boolean wouldCycle(long roleId, long parentId)
    {
        return roleId == parentId || ancestors(parentId).containsKey(roleId);
    }

    private static Map<Long, Integer> walk(long start, Map<Long, List<Long>> edges)
    {
        Map<Long, Integer> found = new LinkedHashMap<>();
        Deque<Long> pending = new ArrayDeque<>(List.of(start));
        Map<Long, Integer> distance = new LinkedHashMap<>(Map.of(start, 0));
        while (!pending.isEmpty()) {
            long current = pending.removeFirst();
            int next = distance.getOrDefault(current, 0) + 1;
            for (long target : edges.getOrDefault(current, List.of())) {
                if (target != start && !found.containsKey(target)) {
                    found.put(target, next);
                    distance.put(target, next);
                    pending.addLast(target);
                }
            }
        }
        return found;
    }
}
