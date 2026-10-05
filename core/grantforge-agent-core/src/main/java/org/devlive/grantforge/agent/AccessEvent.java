// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One access the agent saw, as the server's access audit stores it. Built with {@link #builder}; each event gets its
 * own id, which makes sending it again harmless. Immutable.
 */
public final class AccessEvent
{
    /** Longest request text kept, as the server stores it. */
    public static final int MAX_REQUEST = 1000;

    private final String eventId;
    private final Instant occurredAt;
    private final String user;
    private final String resource;
    private final String accessType;
    private final boolean allowed;
    private final @Nullable String clientIp;
    private final @Nullable String resourceType;
    private final @Nullable String action;
    private final @Nullable Long policyId;
    private final @Nullable Long policyVersion;
    private final boolean byGrantForge;
    private final @Nullable String request;

    AccessEvent(Builder builder)
    {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = builder.occurredAt;
        this.user = builder.user;
        this.resource = builder.resource;
        this.accessType = builder.accessType;
        this.allowed = builder.allowed;
        this.clientIp = builder.clientIp;
        this.resourceType = builder.resourceType;
        this.action = builder.action;
        this.policyId = builder.policyId;
        this.policyVersion = builder.policyVersion;
        this.byGrantForge = builder.byGrantForge;
        String text = builder.request;
        this.request = text == null || text.length() <= MAX_REQUEST ? text : text.substring(0, MAX_REQUEST);
    }

    /**
     * Starts an event.
     *
     * @param user who
     * @param resource the resource, its levels joined as the system shows them, such as {@code /data/sales} or
     *        {@code sales.orders.ssn}
     * @param accessType the access type checked
     * @param allowed whether access was allowed in the end
     * @return a builder
     */
    public static Builder builder(String user, String resource, String accessType, boolean allowed)
    {
        return new Builder(user, resource, accessType, allowed);
    }

    /**
     * Returns the event's id.
     *
     * @return a UUID
     */
    public String eventId()
    {
        return eventId;
    }

    /**
     * Returns the event as the server's access event API takes it.
     *
     * @return the fields, by name
     */
    Map<String, @Nullable Object> fields()
    {
        Map<String, @Nullable Object> fields = new LinkedHashMap<>();
        fields.put("eventId", eventId);
        fields.put("occurredAt", occurredAt.toString());
        fields.put("user", user);
        fields.put("clientIp", clientIp);
        fields.put("resource", resource);
        fields.put("resourceType", resourceType);
        fields.put("accessType", accessType);
        fields.put("action", action);
        fields.put("outcome", allowed ? "ALLOWED" : "DENIED");
        fields.put("policyId", policyId == null ? null : Long.toString(policyId));
        fields.put("policyVersion", policyVersion);
        fields.put("enforcer", byGrantForge ? "GRANTFORGE" : "NATIVE");
        fields.put("request", request);
        return fields;
    }

    /** Collects an event. */
    public static final class Builder
    {
        final String user;
        final String resource;
        final String accessType;
        final boolean allowed;
        Instant occurredAt = Instant.now();
        @Nullable String clientIp;
        @Nullable String resourceType;
        @Nullable String action;
        @Nullable Long policyId;
        @Nullable Long policyVersion;
        boolean byGrantForge;
        @Nullable String request;

        Builder(String user, String resource, String accessType, boolean allowed)
        {
            this.user = user;
            this.resource = resource;
            this.accessType = accessType;
            this.allowed = allowed;
        }

        /**
         * Says who decided: GrantForge when the decision was determined, the system's own checks otherwise.
         *
         * @param decision the agent's decision
         * @return this builder
         */
        public Builder decidedBy(AgentDecision decision)
        {
            this.byGrantForge = decision.determined();
            this.policyId = decision.policyId();
            this.policyVersion = decision.policyVersion();
            return this;
        }

        /**
         * Sets when it happened; now by default.
         *
         * @param value the moment
         * @return this builder
         */
        public Builder occurredAt(Instant value)
        {
            this.occurredAt = value;
            return this;
        }

        /**
         * Sets from where.
         *
         * @param value the client address
         * @return this builder
         */
        public Builder clientIp(@Nullable String value)
        {
            this.clientIp = value;
            return this;
        }

        /**
         * Sets the lowest level of the resource, such as {@code column}.
         *
         * @param value the level
         * @return this builder
         */
        public Builder resourceType(@Nullable String value)
        {
            this.resourceType = value;
            return this;
        }

        /**
         * Sets the system's operation, such as {@code open} or {@code SELECT}.
         *
         * @param value the operation
         * @return this builder
         */
        public Builder action(@Nullable String value)
        {
            this.action = value;
            return this;
        }

        /**
         * Sets the request, such as an SQL statement; cut to {@value #MAX_REQUEST} characters.
         *
         * @param value the request
         * @return this builder
         */
        public Builder request(@Nullable String value)
        {
            this.request = value;
            return this;
        }

        /**
         * Builds the event.
         *
         * @return the event
         */
        public AccessEvent build()
        {
            return new AccessEvent(this);
        }
    }
}
