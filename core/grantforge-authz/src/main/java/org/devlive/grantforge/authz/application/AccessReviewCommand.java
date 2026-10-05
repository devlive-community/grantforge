// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ReviewFallback;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * An access review as an administrator enters it.
 *
 * @param name the name
 * @param description a longer explanation, or {@code null}
 * @param roleIds the roles whose assignments are reviewed, 1 to 50
 * @param durationDays how long each round stays open
 * @param intervalDays days between scheduled rounds, or {@code null} for no repetition
 * @param unreviewed what happens to assignments nobody decided about
 * @param enabled whether rounds start on schedule
 * @param nextRunAt when the next scheduled round starts, or {@code null} for none
 */
public record AccessReviewCommand(String name, @Nullable String description, Set<Long> roleIds, int durationDays, @Nullable Integer intervalDays,
        ReviewFallback unreviewed, boolean enabled, @Nullable Instant nextRunAt)
{
    /** Copies the roles. */
    public AccessReviewCommand
    {
        requireNonNull(name, "name");
        roleIds = Set.copyOf(requireNonNull(roleIds, "roleIds"));
        requireNonNull(unreviewed, "unreviewed");
    }
}
