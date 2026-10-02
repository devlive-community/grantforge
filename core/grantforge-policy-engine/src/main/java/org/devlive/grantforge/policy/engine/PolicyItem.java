// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Whom a policy allows or denies which access types: users, groups and roles, the access types, and conditions that
 * must all hold. The group {@value #PUBLIC} stands for everyone.
 */
public final class PolicyItem
{
    /** The group every user belongs to. */
    public static final String PUBLIC = "public";

    private final Set<String> users;
    private final Set<String> groups;
    private final Set<String> roles;
    private final Set<String> accessTypes;
    private final List<Condition> conditions;

    PolicyItem(Builder builder)
    {
        this.users = Checks.texts(builder.users, "users");
        this.groups = Checks.texts(builder.groups, "groups");
        this.roles = Checks.texts(builder.roles, "roles");
        this.accessTypes = Checks.texts(builder.accessTypes, "access types");
        this.conditions = Checks.list(builder.conditions, "conditions");
        if (users.isEmpty() && groups.isEmpty() && roles.isEmpty()) {
            throw new IllegalArgumentException("a policy item needs at least one user, group or role");
        }
        if (accessTypes.isEmpty()) {
            throw new IllegalArgumentException("a policy item needs at least one access type");
        }
    }

    /**
     * Starts describing an item.
     *
     * @return a builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Returns the users.
     *
     * @return their names
     */
    public Set<String> users()
    {
        return users;
    }

    /**
     * Returns the groups.
     *
     * @return their names
     */
    public Set<String> groups()
    {
        return groups;
    }

    /**
     * Returns the roles.
     *
     * @return their names
     */
    public Set<String> roles()
    {
        return roles;
    }

    /**
     * Returns the access types.
     *
     * @return their names
     */
    public Set<String> accessTypes()
    {
        return accessTypes;
    }

    /**
     * Returns the conditions.
     *
     * @return the conditions, which must all hold
     */
    public List<Condition> conditions()
    {
        return conditions;
    }

    /** Builds a {@link PolicyItem}. */
    public static final class Builder
    {
        Collection<String> users = Collections.emptyList();
        Collection<String> groups = Collections.emptyList();
        Collection<String> roles = Collections.emptyList();
        Collection<String> accessTypes = Collections.emptyList();
        Collection<Condition> conditions = Collections.emptyList();

        Builder()
        {
        }

        /**
         * Sets the users.
         *
         * @param values their names
         * @return this builder
         */
        public Builder users(String... values)
        {
            this.users = Arrays.asList(values);
            return this;
        }

        /**
         * Sets the groups.
         *
         * @param values their names
         * @return this builder
         */
        public Builder groups(String... values)
        {
            this.groups = Arrays.asList(values);
            return this;
        }

        /**
         * Sets the roles.
         *
         * @param values their names
         * @return this builder
         */
        public Builder roles(String... values)
        {
            this.roles = Arrays.asList(values);
            return this;
        }

        /**
         * Sets the access types.
         *
         * @param values their names
         * @return this builder
         */
        public Builder accessTypes(String... values)
        {
            this.accessTypes = Arrays.asList(values);
            return this;
        }

        /**
         * Sets the conditions.
         *
         * @param values the conditions
         * @return this builder
         */
        public Builder conditions(Condition... values)
        {
            this.conditions = Arrays.asList(values);
            return this;
        }

        /**
         * Builds the item.
         *
         * @return the item
         * @throws IllegalArgumentException without a user, group or role, or without an access type
         */
        public PolicyItem build()
        {
            return new PolicyItem(this);
        }
    }
}
