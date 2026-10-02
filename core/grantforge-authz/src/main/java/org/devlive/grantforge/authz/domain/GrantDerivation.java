// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Works out what a role's explicit grants mean for one application's resource tree. Denials win and reach
 * everything below the denied resource. An allowed resource makes its ancestors visible and implies everything it
 * requires, transitively. Every implied or inherited state names its reasons, so administrators see why.
 */
public final class GrantDerivation
{
    private final Map<Long, Resource> resources;
    private final Map<Long, List<Long>> children;
    private final DependencyGraph dependencies;

    /**
     * Prepares the derivation for one application.
     *
     * @param tree the application's resources
     * @param dependencies the application's dependencies
     */
    public GrantDerivation(Collection<Resource> tree, DependencyGraph dependencies)
    {
        this.resources = new LinkedHashMap<>();
        tree.forEach(resource -> resources.put(resource.requireId(), resource));
        this.children = tree.stream().filter(resource -> resource.getParentId() != null)
                .collect(Collectors.groupingBy(resource -> requireNonNull(resource.getParentId()),
                        Collectors.mapping(Resource::requireId, Collectors.toList())));
        this.dependencies = requireNonNull(dependencies, "dependencies");
    }

    /**
     * Derives the state of every resource the grants touch.
     *
     * @param grants the role's grants; expired ones are ignored
     * @param wholeSubtrees resources whose whole subtree the role allows without grants (system roles' modules)
     * @param now the current time, for expiry
     * @return the states of the resources that are allowed, implied or denied; others are absent
     */
    public Map<Long, ResourceState> derive(Collection<RoleGrant> grants, Collection<Long> wholeSubtrees, Instant now)
    {
        Map<Long, ResourceState> states = new LinkedHashMap<>();
        List<RoleGrant> current = grants.stream().filter(grant -> grant.appliesAt(now) && resources.containsKey(grant.getResourceId()))
                .toList();
        // Denials first: they win over every allowance and reach the whole subtree.
        for (RoleGrant grant : current) {
            if (grant.getEffect() == GrantEffect.DENY) {
                states.put(grant.getResourceId(), ResourceState.explicit(State.DENIED));
                for (long below : descendants(grant.getResourceId())) {
                    states.computeIfAbsent(below, key -> ResourceState.derived(State.DENIED))
                            .addReason(grant.getResourceId(), Via.DENIAL);
                }
            }
        }
        List<Long> seeds = new ArrayList<>();
        for (RoleGrant grant : current) {
            if (grant.getEffect() == GrantEffect.ALLOW && !states.containsKey(grant.getResourceId())) {
                states.put(grant.getResourceId(), ResourceState.explicit(State.ALLOWED));
                seeds.add(grant.getResourceId());
            }
        }
        for (long root : wholeSubtrees) {
            for (long covered : withDescendants(root)) {
                ResourceState existing = states.get(covered);
                if (existing == null || existing.state() == State.IMPLIED) {
                    imply(states, covered, root, Via.SYSTEM_ROLE);
                    seeds.add(covered);
                }
            }
        }
        for (long seed : List.copyOf(seeds)) {
            for (long needed : dependencies.requiredBy(List.of(seed))) {
                if (resources.containsKey(needed) && imply(states, needed, seed, Via.DEPENDENCY)) {
                    seeds.add(needed);
                }
            }
        }
        for (long seed : seeds) {
            Long parent = parentOf(seed);
            while (parent != null && resources.containsKey(parent)) {
                imply(states, parent, seed, Via.ANCESTOR);
                parent = parentOf(parent);
            }
        }
        return states;
    }

    /** Implies a resource unless it is explicitly granted or denied; returns whether it was newly implied. */
    private static boolean imply(Map<Long, ResourceState> states, long resource, long because, Via via)
    {
        ResourceState existing = states.get(resource);
        if (existing != null && existing.state() != State.IMPLIED) {
            return false;
        }
        ResourceState state = existing == null ? ResourceState.derived(State.IMPLIED) : existing;
        state.addReason(because, via);
        states.put(resource, state);
        return existing == null;
    }

    private @Nullable Long parentOf(long resource)
    {
        Resource found = resources.get(resource);
        return found == null ? null : found.getParentId();
    }

    private List<Long> descendants(long root)
    {
        List<Long> found = new ArrayList<>();
        Deque<Long> pending = new ArrayDeque<>(children.getOrDefault(root, List.of()));
        while (!pending.isEmpty()) {
            long current = pending.removeFirst();
            found.add(current);
            pending.addAll(children.getOrDefault(current, List.of()));
        }
        return found;
    }

    private List<Long> withDescendants(long root)
    {
        if (!resources.containsKey(root)) {
            return List.of();
        }
        List<Long> all = new ArrayList<>();
        all.add(root);
        all.addAll(descendants(root));
        return all;
    }

    /** What the derivation concluded about one resource. */
    public enum State
    {
        /** Explicitly allowed. */
        ALLOWED,

        /** Allowed because of something else: an allowed descendant, an allowed resource requiring it, a system role. */
        IMPLIED,

        /** Explicitly denied, or below a denied resource. */
        DENIED
    }

    /** Why a resource is implied or denied. */
    public enum Via
    {
        /** It is an ancestor of an allowed resource, so the console shows the way there. */
        ANCESTOR,

        /** An allowed resource requires it. */
        DEPENDENCY,

        /** It lies below a denied resource. */
        DENIAL,

        /** A system role allows its whole module. */
        SYSTEM_ROLE
    }

    /**
     * One reason for a derived state.
     *
     * @param resourceId the resource that causes it
     * @param via how
     */
    public record Reason(long resourceId, Via via)
    {
        /** Checks the kind. */
        public Reason
        {
            requireNonNull(via, "via");
        }
    }

    /** The state of one resource and, when derived, why. */
    public static final class ResourceState
    {
        private final State state;
        private final boolean explicit;
        private final Set<Reason> reasons = new LinkedHashSet<>();

        private ResourceState(State state, boolean explicit)
        {
            this.state = state;
            this.explicit = explicit;
        }

        static ResourceState explicit(State state)
        {
            return new ResourceState(state, true);
        }

        static ResourceState derived(State state)
        {
            return new ResourceState(state, false);
        }

        void addReason(long resourceId, Via via)
        {
            reasons.add(new Reason(resourceId, via));
        }

        /**
         * Returns the state.
         *
         * @return allowed, implied or denied
         */
        public State state()
        {
            return state;
        }

        /**
         * Returns whether a grant of the role sets it directly.
         *
         * @return {@code true} for explicit grants
         */
        public boolean explicit()
        {
            return explicit;
        }

        /**
         * Returns why a derived state holds.
         *
         * @return the reasons, in the order found; empty for explicit grants
         */
        public List<Reason> reasons()
        {
            return List.copyOf(reasons);
        }

        /**
         * Returns whether the resource is usable: allowed or implied.
         *
         * @return {@code false} for denied resources
         */
        public boolean effective()
        {
            return state != State.DENIED;
        }
    }
}
