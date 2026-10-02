// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/** An access an agent checked: who tried what on which resource, and what was decided by which policy. Never changed. */
@Entity
@Table(name = "gf_access_event")
public class AccessEvent
        extends TenantScopedEntity
{
    /** Longest resource and request texts kept. */
    public static final int MAX_TEXT = 1000;

    @Column(name = "service_id", nullable = false, updatable = false)
    private long serviceId;

    @Column(name = "event_id", nullable = false, updatable = false, length = 64)
    private String eventId = "";

    @Column(name = "agent_instance", nullable = false, updatable = false, length = 128)
    private String agentInstance = "";

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.EPOCH;

    @Column(name = "user_name", nullable = false, updatable = false, length = 128)
    private String userName = "";

    @Column(name = "client_ip", updatable = false, length = 45)
    private @Nullable String clientIp;

    @Column(name = "resource_path", nullable = false, updatable = false, length = MAX_TEXT)
    private String resourcePath = "";

    @Column(name = "resource_type", updatable = false, length = 64)
    private @Nullable String resourceType;

    @Column(name = "access_type", nullable = false, updatable = false, length = 64)
    private String accessType = "";

    @Column(name = "action_name", updatable = false, length = 128)
    private @Nullable String actionName;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false, length = 16)
    private AccessOutcome outcome = AccessOutcome.DENIED;

    @Column(name = "policy_id", updatable = false)
    private @Nullable Long policyId;

    @Column(name = "policy_version", updatable = false)
    private @Nullable Long policyVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "enforcer", nullable = false, updatable = false, length = 16)
    private Enforcer enforcer = Enforcer.GRANTFORGE;

    @Column(name = "request_text", updatable = false, length = MAX_TEXT)
    private @Nullable String requestText;

    /** For JPA. */
    protected AccessEvent()
    {
    }

    /**
     * Records an access.
     *
     * @param serviceId the service the agent enforces
     * @param agentInstance the agent that reported it
     * @param fields what happened
     * @return the event
     */
    public static AccessEvent of(long serviceId, String agentInstance, Fields fields)
    {
        AccessEvent event = new AccessEvent();
        event.serviceId = serviceId;
        event.agentInstance = requireNonNull(agentInstance, "agentInstance");
        event.eventId = fields.eventId();
        event.occurredAt = fields.occurredAt();
        event.userName = fields.userName();
        event.clientIp = fields.clientIp();
        event.resourcePath = fields.resourcePath();
        event.resourceType = fields.resourceType();
        event.accessType = fields.accessType();
        event.actionName = fields.actionName();
        event.outcome = fields.outcome();
        event.policyId = fields.policyId();
        event.policyVersion = fields.policyVersion();
        event.enforcer = fields.enforcer();
        event.requestText = fields.requestText();
        return event;
    }

    /**
     * What happened.
     *
     * @param eventId the agent's name for the event, unique within the service
     * @param occurredAt when
     * @param userName who
     * @param clientIp from where
     * @param resourcePath the resource, its levels joined as the agent shows them
     * @param resourceType the lowest level of the resource
     * @param accessType the access type checked
     * @param actionName the operation of the system, such as {@code open} or {@code SELECT}
     * @param outcome allowed or denied
     * @param policyId the policy that decided, or {@code null} if none did
     * @param policyVersion the policy version the agent applied
     * @param enforcer who decided: GrantForge's policies or the system's own permissions
     * @param requestText the request, such as an SQL statement, cut to {@value #MAX_TEXT} characters
     */
    public record Fields(String eventId, Instant occurredAt, String userName, @Nullable String clientIp, String resourcePath,
            @Nullable String resourceType, String accessType, @Nullable String actionName, AccessOutcome outcome, @Nullable Long policyId,
            @Nullable Long policyVersion, Enforcer enforcer, @Nullable String requestText)
    {
        /** Checks the required parts. */
        public Fields
        {
            requireNonNull(eventId, "eventId");
            requireNonNull(occurredAt, "occurredAt");
            requireNonNull(userName, "userName");
            requireNonNull(resourcePath, "resourcePath");
            requireNonNull(accessType, "accessType");
            requireNonNull(outcome, "outcome");
            requireNonNull(enforcer, "enforcer");
        }
    }

    /**
     * Returns what happened.
     *
     * @return the fields
     */
    public Fields fields()
    {
        return new Fields(eventId, occurredAt, userName, clientIp, resourcePath, resourceType, accessType, actionName, outcome, policyId,
                policyVersion, enforcer, requestText);
    }

    /**
     * Returns the service.
     *
     * @return the service's id
     */
    public long getServiceId()
    {
        return serviceId;
    }

    /**
     * Returns the agent that reported the event.
     *
     * @return its instance name
     */
    public String getAgentInstance()
    {
        return agentInstance;
    }

    /**
     * Returns the agent's name for the event.
     *
     * @return the event id
     */
    public String getEventId()
    {
        return eventId;
    }

    /**
     * Returns when the access happened.
     *
     * @return the moment
     */
    public Instant getOccurredAt()
    {
        return occurredAt;
    }
}
