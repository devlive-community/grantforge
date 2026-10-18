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
    private final int count;
    private final @Nullable Instant lastOccurredAt;

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
        this.count = 1;
        this.lastOccurredAt = null;
    }

    // A single access has no last moment.
    @SuppressWarnings("PMD.NullAssignment")
    private AccessEvent(AccessEvent source, int count, Instant lastOccurredAt)
    {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = source.occurredAt;
        this.user = source.user;
        this.resource = source.resource;
        this.accessType = source.accessType;
        this.allowed = source.allowed;
        this.clientIp = source.clientIp;
        this.resourceType = source.resourceType;
        this.action = source.action;
        this.policyId = source.policyId;
        this.policyVersion = source.policyVersion;
        this.byGrantForge = source.byGrantForge;
        this.request = source.request;
        this.count = count;
        this.lastOccurredAt = count > 1 ? lastOccurredAt : null;
    }

    /**
     * Returns this access folded with identical ones: a new event, with its own id, that stands for all of them.
     *
     * @param total how many accesses it stands for, at least one
     * @param last when the last of them happened
     * @return the event
     */
    AccessEvent folded(int total, Instant last)
    {
        if (total < 1) {
            throw new IllegalArgumentException("an event stands for at least one access");
        }
        return new AccessEvent(this, total, last);
    }

    /**
     * Returns what identical accesses share, for folding them: everything but the moment.
     *
     * @return the key
     */
    String foldingKey()
    {
        return String.join("\u001f", user, String.valueOf(clientIp), resource, String.valueOf(resourceType), accessType, String.valueOf(action),
                allowed ? "1" : "0", String.valueOf(policyId), String.valueOf(policyVersion), byGrantForge ? "1" : "0", String.valueOf(request));
    }

    /**
     * Returns when the access happened.
     *
     * @return the moment
     */
    Instant occurredAt()
    {
        return occurredAt;
    }

    /**
     * Returns whether access was allowed.
     *
     * @return {@code true} if it was
     */
    public boolean allowed()
    {
        return allowed;
    }

    /**
     * Returns how many identical accesses the event stands for.
     *
     * @return 1 unless folded
     */
    public int count()
    {
        return count;
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
        if (count > 1) {
            fields.put("count", count);
            fields.put("lastOccurredAt", lastOccurredAt == null ? null : lastOccurredAt.toString());
        }
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
         * Credits GrantForge for enforcing access without a matching policy, such as an agent's strict default deny.
         * Call after {@link #decidedBy} when retaining the snapshot version of an undetermined decision.
         *
         * @return this builder
         */
        public Builder enforcedByGrantForge()
        {
            this.byGrantForge = true;
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
