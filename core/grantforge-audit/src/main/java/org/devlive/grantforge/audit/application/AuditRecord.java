// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.application;

import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * An event to audit; the time and request origin are added when it is recorded.
 *
 * @param action what happened
 * @param outcome whether it took place
 * @param tenantId the tenant concerned, if known
 * @param actorId the account that acted (or claimed to), if known
 * @param actorName the actor's login name as given, if any
 * @param targetId what the action applied to, if anything
 * @param reason why it was refused or happened, such as an error code
 */
public record AuditRecord(
        AuditAction action,
        AuditOutcome outcome,
        @Nullable Long tenantId,
        @Nullable Long actorId,
        @Nullable String actorName,
        @Nullable String targetId,
        @Nullable String reason)
{
    /** Validates the values. */
    public AuditRecord
    {
        requireNonNull(action, "action");
        requireNonNull(outcome, "outcome");
    }
}
