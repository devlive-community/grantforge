// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A policy checked against the service model, with its values compiled into matchers. */
final class CompiledPolicy
{
    private final Policy policy;
    private final String root;
    private final Map<String, Level> levels;

    CompiledPolicy(Policy policy, ServiceModel model)
    {
        this.policy = policy;
        List<ResourceLevel> chain = deepestChain(policy, model);
        Set<String> names = new LinkedHashSet<>();
        for (ResourceLevel level : chain) {
            names.add(level.name());
        }
        if (!names.equals(policy.resources().keySet())) {
            throw new IllegalArgumentException("policy " + policy.id() + " must give values for every level from "
                    + chain.get(0).name() + " down to " + chain.get(chain.size() - 1).name() + ", not " + policy.resources().keySet());
        }
        this.root = chain.get(0).name();
        Map<String, Level> compiled = new LinkedHashMap<>();
        for (ResourceLevel level : chain) {
            compiled.put(level.name(), level(level, policy));
        }
        this.levels = Collections.unmodifiableMap(compiled);
        checkAccessTypes(policy, model);
    }

    private static Level level(ResourceLevel level, Policy policy)
    {
        return new Level(level, Checks.notNull(policy.resources().get(level.name()), level.name()));
    }

    private static List<ResourceLevel> deepestChain(Policy policy, ServiceModel model)
    {
        List<ResourceLevel> deepest = Collections.emptyList();
        for (String name : policy.resources().keySet()) {
            if (model.level(name) == null) {
                throw new IllegalArgumentException("policy " + policy.id() + " names an unknown resource level " + name);
            }
            List<ResourceLevel> chain = model.chain(name);
            if (chain.size() > deepest.size()) {
                deepest = chain;
            }
        }
        return deepest;
    }

    private static void checkAccessTypes(Policy policy, ServiceModel model)
    {
        List<PolicyItem> items = new ArrayList<>(policy.allow());
        items.addAll(policy.allowExceptions());
        items.addAll(policy.deny());
        items.addAll(policy.denyExceptions());
        for (PolicyItem item : items) {
            for (String accessType : item.accessTypes()) {
                if (!model.hasAccessType(accessType)) {
                    throw new IllegalArgumentException("policy " + policy.id() + " names an unknown access type " + accessType);
                }
            }
        }
    }

    Policy policy()
    {
        return policy;
    }

    String root()
    {
        return root;
    }

    /** The matchers of the root level, for the prefix index; empty if the root excludes values. */
    Level rootLevel()
    {
        return Checks.notNull(levels.get(root), root);
    }

    /**
     * Returns whether the policy covers a requested resource: the same root; a matching value for every requested
     * level; and {@code *} for levels the request does not reach.
     */
    boolean covers(Map<String, String> normalized)
    {
        for (Map.Entry<String, String> requested : normalized.entrySet()) {
            Level level = levels.get(requested.getKey());
            if (level == null || !level.matches(requested.getValue())) {
                return false;
            }
        }
        for (Map.Entry<String, Level> level : levels.entrySet()) {
            if (!normalized.containsKey(level.getKey()) && !level.getValue().spec.matchesAnything()) {
                return false;
            }
        }
        return true;
    }

    /** The compiled values of one level. */
    static final class Level
    {
        final ResourceSpec spec;
        private final List<ValueMatcher> matchers;

        Level(ResourceLevel level, ResourceSpec spec)
        {
            this.spec = spec;
            List<ValueMatcher> compiled = new ArrayList<>();
            for (String value : spec.values()) {
                compiled.add(ValueMatcher.compile(level, value, spec.recursive()));
            }
            this.matchers = Collections.unmodifiableList(compiled);
        }

        boolean excludes()
        {
            return spec.excludes();
        }

        List<ValueMatcher> matchers()
        {
            return matchers;
        }

        boolean matches(String value)
        {
            boolean any = false;
            for (ValueMatcher matcher : matchers) {
                if (matcher.matches(value)) {
                    any = true;
                    break;
                }
            }
            return any != spec.excludes();
        }
    }
}
