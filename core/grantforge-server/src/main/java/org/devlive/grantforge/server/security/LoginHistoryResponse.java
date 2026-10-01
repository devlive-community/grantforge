// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.audit.application.AuditEntry;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * One entry of the signed-in user's login history.
 *
 * @param occurredAt when it happened
 * @param action a sign-in, a refused sign-in, a lockout or a sign-out
 * @param outcome whether it took place
 * @param reason the error code of a refused sign-in, such as {@code GF-IDENTITY-020}
 * @param clientIp the client's address, if known
 * @param userAgent the client's user agent, if known
 */
public record LoginHistoryResponse(Instant occurredAt, AuditAction action, AuditOutcome outcome, @Nullable String reason,
        @Nullable String clientIp, @Nullable String userAgent)
{
    /**
     * Converts an audit entry.
     *
     * @param entry the entry
     * @return the response
     */
    public static LoginHistoryResponse from(AuditEntry entry)
    {
        return new LoginHistoryResponse(entry.occurredAt(), entry.action(), entry.outcome(), entry.reason(),
                entry.clientIp(), entry.userAgent());
    }
}
