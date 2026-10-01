// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * The dependencies of one application as a directed graph, in memory: answers whether a new edge would close a
 * cycle and what a set of resources requires, transitively. Immutable and cheap to build from all rows of an
 * application.
 */
public final class DependencyGraph
{
    private final Map<Long, List<ResourceDependency>> outgoing;

    /**
     * Builds the graph.
     *
     * @param dependencies the dependencies of one application
     */
    public DependencyGraph(Collection<ResourceDependency> dependencies)
    {
        outgoing = requireNonNull(dependencies, "dependencies").stream()
                .collect(Collectors.groupingBy(ResourceDependency::getResourceId));
    }

    /**
     * Returns whether making {@code from} depend on {@code to} would close a cycle, that is whether {@code to}
     * already (transitively) depends on {@code from}, of any kind.
     *
     * @param from the resource that would depend
     * @param to the resource it would depend on
     * @return {@code true} if the edge would close a cycle (also for {@code from == to})
     */
    public boolean wouldCycle(long from, long to)
    {
        return from == to || reachable(Set.of(to), false).contains(from);
    }

    /**
     * Returns everything a set of resources requires, transitively, following only {@link DependencyKind#REQUIRED}
     * dependencies; the resources themselves are not included unless something in the set requires them.
     *
     * @param resourceIds the starting resources
     * @return the required resources, in breadth-first order
     */
    public Set<Long> requiredBy(Collection<Long> resourceIds)
    {
        return reachable(resourceIds, true);
    }

    private Set<Long> reachable(Collection<Long> starts, boolean requiredOnly)
    {
        Set<Long> seen = new LinkedHashSet<>();
        Deque<Long> pending = new ArrayDeque<>(starts);
        Set<Long> visited = new LinkedHashSet<>(starts);
        while (!pending.isEmpty()) {
            long current = pending.removeFirst();
            for (ResourceDependency edge : outgoing.getOrDefault(current, List.of())) {
                if (requiredOnly && edge.getKind() != DependencyKind.REQUIRED) {
                    continue;
                }
                long next = edge.getDependsOnId();
                seen.add(next);
                if (visited.add(next)) {
                    pending.addLast(next);
                }
            }
        }
        // Starting points appear only when reached through an edge.
        return seen;
    }
}
