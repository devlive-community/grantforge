// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.application;

import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An audit event as searches return it.
 *
 * @param id the event
 * @param occurredAt when it happened
 * @param action what happened
 * @param outcome whether it took place
 * @param tenantId the tenant it happened in, if any
 * @param actorId the account that acted, if known
 * @param actorName the name the actor gave or had, if known
 * @param targetId what it happened to, if anything
 * @param reason more about it, such as an error code or a count
 * @param clientIp the client's address, if known
 * @param userAgent the client's browser, if known
 * @param requestId the request it happened in, if known
 */
public record AuditEventView(long id, Instant occurredAt, AuditAction action, AuditOutcome outcome, @Nullable Long tenantId,
        @Nullable Long actorId, @Nullable String actorName, @Nullable String targetId, @Nullable String reason, @Nullable String clientIp,
        @Nullable String userAgent, @Nullable String requestId)
{
    /**
     * Converts a stored event.
     *
     * @param event the event
     * @return the view
     */
    public static AuditEventView from(AuditEvent event)
    {
        return new AuditEventView(event.requireId(), event.getOccurredAt(), event.getAction(), event.getOutcome(), event.getTenantId(),
                event.getActorId(), event.getActorName(), event.getTargetId(), event.getReason(), event.getClientIp(), event.getUserAgent(),
                event.getRequestId());
    }
}
