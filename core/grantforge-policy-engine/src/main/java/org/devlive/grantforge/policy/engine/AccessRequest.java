// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A request to access a resource: who (a user, the user's groups and roles), what (the resource, as values per level
 * from a root down), how (an access type), when, and anything conditions look at (such as the client address).
 */
public final class AccessRequest
{
    private final Map<String, String> resource;
    private final String accessType;
    private final String user;
    private final Set<String> groups;
    private final Set<String> roles;
    private final Instant time;
    private final Map<String, Object> context;

    AccessRequest(Builder builder)
    {
        this.resource = Checks.map(builder.resource, "resource");
        if (resource.isEmpty()) {
            throw new IllegalArgumentException("an access request names a resource");
        }
        this.accessType = Checks.text(builder.accessType, "access type");
        this.user = Checks.text(builder.user, "user");
        this.groups = Checks.texts(builder.groups, "groups");
        this.roles = Checks.texts(builder.roles, "roles");
        this.time = Checks.notNull(builder.time, "time");
        this.context = Checks.map(builder.context, "context");
    }

    /**
     * Starts describing a request.
     *
     * @param user the user asking
     * @param accessType the access type asked for
     * @return a builder
     */
    public static Builder builder(String user, String accessType)
    {
        return new Builder(user, accessType);
    }

    /**
     * Returns the resource, as values per level.
     *
     * @return the values, from a root down
     */
    public Map<String, String> resource()
    {
        return resource;
    }

    /**
     * Returns the access type asked for.
     *
     * @return the access type
     */
    public String accessType()
    {
        return accessType;
    }

    /**
     * Returns the user.
     *
     * @return the user's name
     */
    public String user()
    {
        return user;
    }

    /**
     * Returns the user's groups.
     *
     * @return their names
     */
    public Set<String> groups()
    {
        return groups;
    }

    /**
     * Returns the user's roles.
     *
     * @return their names
     */
    public Set<String> roles()
    {
        return roles;
    }

    /**
     * Returns when access is asked for.
     *
     * @return the moment
     */
    public Instant time()
    {
        return time;
    }

    /**
     * Returns what conditions look at, such as {@code clientAddress}.
     *
     * @return the values by name
     */
    public Map<String, Object> context()
    {
        return context;
    }

    /** Builds an {@link AccessRequest}. */
    public static final class Builder
    {
        final String user;
        final String accessType;
        final Map<String, String> resource = new LinkedHashMap<>();
        Set<String> groups = Collections.emptySet();
        Set<String> roles = Collections.emptySet();
        Instant time = Instant.now();
        Map<String, Object> context = Collections.emptyMap();

        Builder(String user, String accessType)
        {
            this.user = user;
            this.accessType = accessType;
        }

        /**
         * Adds the value of a resource level; from the root down.
         *
         * @param level the level's name
         * @param value the value
         * @return this builder
         */
        public Builder resource(String level, String value)
        {
            resource.put(Checks.text(level, "level"), Checks.text(value, "resource value"));
            return this;
        }

        /**
         * Sets the user's groups.
         *
         * @param values their names
         * @return this builder
         */
        public Builder groups(String... values)
        {
            this.groups = new LinkedHashSet<>(Arrays.asList(values));
            return this;
        }

        /**
         * Sets the user's roles.
         *
         * @param values their names
         * @return this builder
         */
        public Builder roles(String... values)
        {
            this.roles = new LinkedHashSet<>(Arrays.asList(values));
            return this;
        }

        /**
         * Sets when access is asked for; now unless set.
         *
         * @param value the moment
         * @return this builder
         */
        public Builder time(Instant value)
        {
            this.time = Checks.notNull(value, "time");
            return this;
        }

        /**
         * Sets what conditions look at.
         *
         * @param values the values by name
         * @return this builder
         */
        public Builder context(Map<String, Object> values)
        {
            this.context = Checks.notNull(values, "context");
            return this;
        }

        /**
         * Builds the request.
         *
         * @return the request
         */
        public AccessRequest build()
        {
            return new AccessRequest(this);
        }
    }
}
