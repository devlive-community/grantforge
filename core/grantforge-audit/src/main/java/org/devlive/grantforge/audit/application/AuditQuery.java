// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.application;

import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Filters of an audit search; {@code null} means no filter.
 *
 * @param action the kind of event
 * @param outcome whether the action took place
 * @param actor text the actor's name contains
 * @param target the target's ID
 * @param from the earliest time, inclusive
 * @param until the latest time, exclusive
 */
public record AuditQuery(@Nullable AuditAction action, @Nullable AuditOutcome outcome, @Nullable String actor, @Nullable String target,
        @Nullable Instant from, @Nullable Instant until)
{
    /** No filter. */
    public static final AuditQuery ALL = new AuditQuery(null, null, null, null, null, null);
}
