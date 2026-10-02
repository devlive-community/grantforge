// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Allows and denies access types on some resources: allow items, minus allow exceptions; deny items, minus deny
 * exceptions. A policy applies while it is enabled and, if it has validity periods, within one of them.
 */
public final class Policy
{
    private final long id;
    private final Priority priority;
    private final boolean enabled;
    private final List<Validity> validity;
    private final Map<String, ResourceSpec> resources;
    private final List<PolicyItem> allow;
    private final List<PolicyItem> allowExceptions;
    private final List<PolicyItem> deny;
    private final List<PolicyItem> denyExceptions;

    Policy(Builder builder)
    {
        this.id = builder.id;
        this.priority = Checks.notNull(builder.priority, "priority");
        this.enabled = builder.enabled;
        this.validity = Checks.list(builder.validity, "validity");
        this.resources = Checks.map(builder.resources, "resources");
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("policy " + id + " names no resource");
        }
        this.allow = Checks.list(builder.allow, "allow items");
        this.allowExceptions = Checks.list(builder.allowExceptions, "allow exceptions");
        this.deny = Checks.list(builder.deny, "deny items");
        this.denyExceptions = Checks.list(builder.denyExceptions, "deny exceptions");
    }

    /**
     * Starts describing a policy.
     *
     * @param id the policy's id, reported in decisions
     * @return a builder
     */
    public static Builder builder(long id)
    {
        return new Builder(id);
    }

    /**
     * Returns the id.
     *
     * @return the id
     */
    public long id()
    {
        return id;
    }

    /**
     * Returns the priority.
     *
     * @return the priority
     */
    public Priority priority()
    {
        return priority;
    }

    /**
     * Returns whether the policy applies at a moment: it is enabled and, if it has validity periods, the moment lies
     * in one of them.
     *
     * @param moment the moment
     * @return {@code true} if it applies
     */
    public boolean appliesAt(Instant moment)
    {
        if (!enabled) {
            return false;
        }
        if (validity.isEmpty()) {
            return true;
        }
        for (Validity period : validity) {
            if (period.contains(moment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the resources, by level.
     *
     * @return the values per level
     */
    public Map<String, ResourceSpec> resources()
    {
        return resources;
    }

    /**
     * Returns the allow items.
     *
     * @return the items
     */
    public List<PolicyItem> allow()
    {
        return allow;
    }

    /**
     * Returns the allow exceptions.
     *
     * @return the items
     */
    public List<PolicyItem> allowExceptions()
    {
        return allowExceptions;
    }

    /**
     * Returns the deny items.
     *
     * @return the items
     */
    public List<PolicyItem> deny()
    {
        return deny;
    }

    /**
     * Returns the deny exceptions.
     *
     * @return the items
     */
    public List<PolicyItem> denyExceptions()
    {
        return denyExceptions;
    }

    /** Builds a {@link Policy}. */
    public static final class Builder
    {
        final long id;
        Priority priority = Priority.NORMAL;
        boolean enabled = true;
        final List<Validity> validity = new ArrayList<>();
        final Map<String, ResourceSpec> resources = new LinkedHashMap<>();
        List<PolicyItem> allow = Collections.emptyList();
        List<PolicyItem> allowExceptions = Collections.emptyList();
        List<PolicyItem> deny = Collections.emptyList();
        List<PolicyItem> denyExceptions = Collections.emptyList();

        Builder(long id)
        {
            this.id = id;
        }

        /**
         * Sets the priority.
         *
         * @param value the priority
         * @return this builder
         */
        public Builder priority(Priority value)
        {
            this.priority = value;
            return this;
        }

        /**
         * Enables or disables the policy.
         *
         * @param value whether it applies
         * @return this builder
         */
        public Builder enabled(boolean value)
        {
            this.enabled = value;
            return this;
        }

        /**
         * Adds a validity period.
         *
         * @param value the period
         * @return this builder
         */
        public Builder validity(Validity value)
        {
            validity.add(Checks.notNull(value, "validity"));
            return this;
        }

        /**
         * Sets the values for a resource level.
         *
         * @param level the level's name
         * @param spec the values
         * @return this builder
         */
        public Builder resource(String level, ResourceSpec spec)
        {
            resources.put(Checks.text(level, "level"), Checks.notNull(spec, "spec"));
            return this;
        }

        /**
         * Sets the allow items.
         *
         * @param values the items
         * @return this builder
         */
        public Builder allow(PolicyItem... values)
        {
            this.allow = Arrays.asList(values);
            return this;
        }

        /**
         * Sets the allow exceptions.
         *
         * @param values the items
         * @return this builder
         */
        public Builder allowExceptions(PolicyItem... values)
        {
            this.allowExceptions = Arrays.asList(values);
            return this;
        }

        /**
         * Sets the deny items.
         *
         * @param values the items
         * @return this builder
         */
        public Builder deny(PolicyItem... values)
        {
            this.deny = Arrays.asList(values);
            return this;
        }

        /**
         * Sets the deny exceptions.
         *
         * @param values the items
         * @return this builder
         */
        public Builder denyExceptions(PolicyItem... values)
        {
            this.denyExceptions = Arrays.asList(values);
            return this;
        }

        /**
         * Builds the policy.
         *
         * @return the policy
         * @throws IllegalArgumentException without a resource
         */
        public Policy build()
        {
            return new Policy(this);
        }
    }
}
