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

import static java.util.Objects.requireNonNull;

/**
 * An audit event as shown to the user it concerns.
 *
 * @param occurredAt when it happened
 * @param action what happened
 * @param outcome whether it took place
 * @param reason why it was refused or happened, if recorded
 * @param clientIp the client's address, if known
 * @param userAgent the client's user agent, if known
 */
public record AuditEntry(Instant occurredAt, AuditAction action, AuditOutcome outcome, @Nullable String reason,
        @Nullable String clientIp, @Nullable String userAgent)
{
    /** Validates the values. */
    public AuditEntry
    {
        requireNonNull(occurredAt, "occurredAt");
        requireNonNull(action, "action");
        requireNonNull(outcome, "outcome");
    }

    /**
     * Converts an event.
     *
     * @param event the event
     * @return the entry
     */
    public static AuditEntry from(AuditEvent event)
    {
        return new AuditEntry(event.getOccurredAt(), event.getAction(), event.getOutcome(), event.getReason(),
                event.getClientIp(), event.getUserAgent());
    }
}
