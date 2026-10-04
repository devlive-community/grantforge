// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.devlive.grantforge.common.lang.Strings;
import org.devlive.grantforge.persistence.entity.BaseEntity;
import org.devlive.grantforge.persistence.secured.FilterableField;
import org.devlive.grantforge.persistence.secured.SecuredEntity;
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * One security event, written once and never changed. Not tenant-scoped: a refused sign-in may name no known
 * account or tenant, and platform administrators read across tenants; queries filter by tenant or actor.
 */
@Entity
@Immutable
@Table(name = "gf_audit_event")
@SecuredEntity(code = "audit-event", name = "Audit events", owner = "actorId", unitFromOwner = true, tenant = "tenantId")
public class AuditEvent
        extends BaseEntity
{
    /** Longest stored actor name. */
    public static final int MAX_ACTOR_NAME = 64;

    /** Longest stored client address. */
    public static final int MAX_CLIENT_IP = 45;

    /** Longest stored user agent; longer values are cut. */
    public static final int MAX_USER_AGENT = 255;

    /** Longest stored target ID, reason or request ID. */
    public static final int MAX_CODE = 64;

    @FilterableField("Time")
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private @Nullable Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @FilterableField("Action")
    @Column(name = "action", nullable = false, updatable = false, length = 64)
    private AuditAction action = AuditAction.LOGIN_FAILED;

    @Enumerated(EnumType.STRING)
    @FilterableField("Outcome")
    @Column(name = "outcome", nullable = false, updatable = false, length = 16)
    private AuditOutcome outcome = AuditOutcome.FAILURE;

    @Column(name = "tenant_id", updatable = false)
    private @Nullable Long tenantId;

    @Column(name = "actor_id", updatable = false)
    private @Nullable Long actorId;

    @FilterableField("Actor")
    @Column(name = "actor_name", updatable = false, length = MAX_ACTOR_NAME)
    private @Nullable String actorName;

    @Column(name = "target_id", updatable = false, length = MAX_CODE)
    private @Nullable String targetId;

    @Column(name = "reason", updatable = false, length = MAX_CODE)
    private @Nullable String reason;

    @FilterableField("Client address")
    @Column(name = "client_ip", updatable = false, length = MAX_CLIENT_IP)
    private @Nullable String clientIp;

    @Column(name = "user_agent", updatable = false, length = MAX_USER_AGENT)
    private @Nullable String userAgent;

    @Column(name = "request_id", updatable = false, length = MAX_CODE)
    private @Nullable String requestId;

    /** For JPA. */
    protected AuditEvent()
    {
    }

    /**
     * Creates an event; text values are trimmed, blank ones dropped and long ones cut to their column.
     *
     * @param occurredAt when it happened
     * @param action what happened
     * @param outcome whether it took place
     * @param tenantId the tenant concerned, if known
     * @param actorId the account that acted (or claimed to), if known
     * @param actorName the actor's login name as given, if any
     * @param targetId what the action applied to (for example the account whose session ended), if anything
     * @param reason why it was refused or happened, such as an error code
     * @param clientIp the client's address, if known
     * @param userAgent the client's user agent, if known
     * @param requestId the request's correlation ID, if any
     * @return the event
     */
    public static AuditEvent of(Instant occurredAt, AuditAction action, AuditOutcome outcome, @Nullable Long tenantId,
            @Nullable Long actorId, @Nullable String actorName, @Nullable String targetId, @Nullable String reason,
            @Nullable String clientIp, @Nullable String userAgent, @Nullable String requestId)
    {
        AuditEvent event = new AuditEvent();
        event.occurredAt = requireNonNull(occurredAt, "occurredAt");
        event.action = requireNonNull(action, "action");
        event.outcome = requireNonNull(outcome, "outcome");
        event.tenantId = tenantId;
        event.actorId = actorId;
        event.actorName = cut(actorName, MAX_ACTOR_NAME);
        event.targetId = cut(targetId, MAX_CODE);
        event.reason = cut(reason, MAX_CODE);
        event.clientIp = cut(clientIp, MAX_CLIENT_IP);
        event.userAgent = cut(userAgent, MAX_USER_AGENT);
        event.requestId = cut(requestId, MAX_CODE);
        return event;
    }

    private static @Nullable String cut(@Nullable String value, int max)
    {
        String text = Strings.blankToNull(value);
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }

    /**
     * Returns when it happened.
     *
     * @return the time
     */
    public Instant getOccurredAt()
    {
        return requireNonNull(occurredAt, "occurredAt");
    }

    /**
     * Returns what happened.
     *
     * @return the action
     */
    public AuditAction getAction()
    {
        return action;
    }

    /**
     * Returns whether it took place.
     *
     * @return the outcome
     */
    public AuditOutcome getOutcome()
    {
        return outcome;
    }

    /**
     * Returns the tenant concerned.
     *
     * @return the tenant ID, or {@code null}
     */
    public @Nullable Long getTenantId()
    {
        return tenantId;
    }

    /**
     * Returns the account that acted.
     *
     * @return the account ID, or {@code null}
     */
    public @Nullable Long getActorId()
    {
        return actorId;
    }

    /**
     * Returns the actor's login name as given.
     *
     * @return the name, or {@code null}
     */
    public @Nullable String getActorName()
    {
        return actorName;
    }

    /**
     * Returns what the action applied to.
     *
     * @return the target ID, or {@code null}
     */
    public @Nullable String getTargetId()
    {
        return targetId;
    }

    /**
     * Returns why it was refused or happened.
     *
     * @return the reason, or {@code null}
     */
    public @Nullable String getReason()
    {
        return reason;
    }

    /**
     * Returns the client's address.
     *
     * @return the address, or {@code null}
     */
    public @Nullable String getClientIp()
    {
        return clientIp;
    }

    /**
     * Returns the client's user agent.
     *
     * @return the user agent, or {@code null}
     */
    public @Nullable String getUserAgent()
    {
        return userAgent;
    }

    /**
     * Returns the request's correlation ID.
     *
     * @return the request ID, or {@code null}
     */
    public @Nullable String getRequestId()
    {
        return requestId;
    }
}
