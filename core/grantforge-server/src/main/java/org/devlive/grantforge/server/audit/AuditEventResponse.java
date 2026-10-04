// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.audit;

import org.devlive.grantforge.audit.application.AuditEventView;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.persistence.secured.SecuredField;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An audit event. IDs are strings, as they exceed JavaScript's safe integers.
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
public record AuditEventResponse(String id, Instant occurredAt, AuditAction action, AuditOutcome outcome, @Nullable String tenantId,
        @Nullable String actorId, @Nullable String actorName, @Nullable String targetId, @Nullable String reason,
        @SecuredField(entity = "audit-event", field = "clientIp", name = "Client IP") @Nullable String clientIp,
        @SecuredField(entity = "audit-event", field = "userAgent", name = "User agent") @Nullable String userAgent,
        @Nullable String requestId)
{
    /**
     * Converts an event.
     *
     * @param event the event
     * @return the response
     */
    public static AuditEventResponse from(AuditEventView event)
    {
        return new AuditEventResponse(Long.toString(event.id()), event.occurredAt(), event.action(), event.outcome(), text(event.tenantId()),
                text(event.actorId()), event.actorName(), event.targetId(), event.reason(), event.clientIp(), event.userAgent(),
                event.requestId());
    }

    private static @Nullable String text(@Nullable Long id)
    {
        return id == null ? null : Long.toString(id);
    }
}
