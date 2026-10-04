// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ReviewDecision;
import org.devlive.grantforge.authz.domain.ReviewOutcome;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * An assignment under review as the console shows it.
 *
 * @param id the item ID
 * @param role the role
 * @param subject who has it; named by its ID if it was deleted
 * @param assigned whether the assignment still exists
 * @param validFrom when the assignment starts, or {@code null}
 * @param validTo when the assignment ends, or {@code null}
 * @param includeSubUnits for a department: whether the assignment includes its sub-departments
 * @param decision what the reviewers decided
 * @param decidedBy who decided, or {@code null}
 * @param decidedAt when, or {@code null}
 * @param comment what the reviewer said, or {@code null}
 * @param outcome what completing the round did, or {@code null} before
 */
public record ReviewItemView(long id, RoleView role, Subject subject, boolean assigned, @Nullable Instant validFrom,
        @Nullable Instant validTo, boolean includeSubUnits, ReviewDecision decision,
        @Nullable Subject decidedBy, @Nullable Instant decidedAt, @Nullable String comment, @Nullable ReviewOutcome outcome)
{
    /** Validates the values. */
    public ReviewItemView
    {
        requireNonNull(role, "role");
        requireNonNull(subject, "subject");
        requireNonNull(decision, "decision");
    }
}
