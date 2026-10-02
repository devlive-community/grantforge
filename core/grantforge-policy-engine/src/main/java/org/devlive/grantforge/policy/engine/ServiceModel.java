// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the engine needs of a service type: its resource levels, which form one or more chains from a root (Hive:
 * {@code database → table → column} and {@code url}), and its access types with what each implies (Hive's
 * {@code all} implies {@code select}, {@code update}, ...).
 */
public final class ServiceModel
{
    private final Map<String, ResourceLevel> levels;
    private final Map<String, Set<String>> grantedBy;

    ServiceModel(Map<String, ResourceLevel> levels, Map<String, Set<String>> implies)
    {
        this.levels = levels;
        Map<String, Set<String>> granted = new LinkedHashMap<>();
        for (String requested : implies.keySet()) {
            granted.put(requested, grantersOf(requested, implies));
        }
        this.grantedBy = Collections.unmodifiableMap(granted);
    }

    /**
     * Starts describing a service type.
     *
     * @return a builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    private static Set<String> grantersOf(String requested, Map<String, Set<String>> implies)
    {
        Set<String> by = new LinkedHashSet<>();
        for (String type : implies.keySet()) {
            if (closure(type, implies).contains(requested)) {
                by.add(type);
            }
        }
        return Collections.unmodifiableSet(by);
    }

    private static Set<String> closure(String type, Map<String, Set<String>> implies)
    {
        Set<String> found = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(type);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (found.add(current)) {
                Set<String> next = implies.get(current);
                if (next != null) {
                    pending.addAll(next);
                }
            }
        }
        return found;
    }

    /**
     * Returns a level.
     *
     * @param name the level's name
     * @return the level, or {@code null} if the service type has none of that name
     */
    public @Nullable ResourceLevel level(String name)
    {
        return levels.get(name);
    }

    /**
     * Returns the levels from the root down to a level.
     *
     * @param name the level's name
     * @return the chain, root first
     * @throws IllegalArgumentException for an unknown level
     */
    public List<ResourceLevel> chain(String name)
    {
        ResourceLevel level = levels.get(name);
        if (level == null) {
            throw new IllegalArgumentException("unknown resource level " + name);
        }
        List<ResourceLevel> chain = new ArrayList<>();
        chain.add(level);
        for (String parent = level.parent(); parent != null; parent = chain.get(0).parent()) {
            chain.add(0, Checks.notNull(levels.get(parent), parent));
        }
        return chain;
    }

    /**
     * Returns the access types that grant an access type: itself and every type that implies it, directly or not.
     *
     * @param accessType the requested access type
     * @return the granting types; empty for an unknown access type
     */
    public Set<String> grantedBy(String accessType)
    {
        Set<String> by = grantedBy.get(accessType);
        return by == null ? Collections.<String>emptySet() : by;
    }

    /**
     * Returns whether the service type has an access type.
     *
     * @param accessType the access type
     * @return {@code true} if it is known
     */
    public boolean hasAccessType(String accessType)
    {
        return grantedBy.containsKey(accessType);
    }

    /** Builds a {@link ServiceModel}. */
    public static final class Builder
    {
        private final Map<String, ResourceLevel> levels = new LinkedHashMap<>();
        private final Map<String, Set<String>> implies = new LinkedHashMap<>();

        Builder()
        {
        }

        /**
         * Adds a resource level; parents before their children.
         *
         * @param level the level
         * @return this builder
         * @throws IllegalArgumentException for a repeated name or an unknown parent
         */
        public Builder level(ResourceLevel level)
        {
            Checks.notNull(level, "level");
            if (levels.containsKey(level.name())) {
                throw new IllegalArgumentException("resource level " + level.name() + " is defined twice");
            }
            String parent = level.parent();
            if (parent != null && !levels.containsKey(parent)) {
                throw new IllegalArgumentException("resource level " + level.name() + " has an unknown parent " + parent);
            }
            levels.put(level.name(), level);
            return this;
        }

        /**
         * Adds an access type.
         *
         * @param name the access type's name
         * @param implied the access types it implies; they must be added too
         * @return this builder
         * @throws IllegalArgumentException for a repeated name
         */
        public Builder accessType(String name, String... implied)
        {
            Checks.text(name, "access type");
            if (implies.containsKey(name)) {
                throw new IllegalArgumentException("access type " + name + " is defined twice");
            }
            implies.put(name, Checks.texts(Arrays.asList(implied), "implied access types of " + name));
            return this;
        }

        /**
         * Builds the model.
         *
         * @return the model
         * @throws IllegalArgumentException without levels or access types, or for an implied access type that is not
         *         defined
         */
        public ServiceModel build()
        {
            if (levels.isEmpty() || implies.isEmpty()) {
                throw new IllegalArgumentException("a service model needs at least one resource level and access type");
            }
            Set<String> known = new HashSet<>(implies.keySet());
            for (Map.Entry<String, Set<String>> entry : implies.entrySet()) {
                for (String implied : entry.getValue()) {
                    if (!known.contains(implied)) {
                        throw new IllegalArgumentException("access type " + entry.getKey() + " implies unknown " + implied);
                    }
                }
            }
            return new ServiceModel(Collections.unmodifiableMap(new LinkedHashMap<>(levels)),
                    Collections.unmodifiableMap(new LinkedHashMap<>(implies)));
        }
    }
}
