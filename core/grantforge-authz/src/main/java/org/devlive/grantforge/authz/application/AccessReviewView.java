// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ReviewFallback;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * An access review as the console shows it.
 *
 * @param id the ID
 * @param name the name
 * @param description the explanation, if any
 * @param roles the roles whose assignments are reviewed, by name
 * @param durationDays how long each round stays open
 * @param intervalDays days between scheduled rounds, or {@code null}
 * @param unreviewed what happens to undecided assignments
 * @param enabled whether rounds start on schedule
 * @param nextRunAt when the next scheduled round starts, or {@code null}
 * @param lastStartedAt when the last round started, or {@code null}
 * @param openRound the round reviewers work on now, or {@code null}
 */
public record AccessReviewView(long id, String name, @Nullable String description, List<RoleView> roles, int durationDays,
        @Nullable Integer intervalDays, ReviewFallback unreviewed, boolean enabled, @Nullable Instant nextRunAt, @Nullable Instant lastStartedAt,
        @Nullable ReviewRoundView openRound)
{
    /** Copies the roles. */
    public AccessReviewView
    {
        requireNonNull(name, "name");
        roles = List.copyOf(requireNonNull(roles, "roles"));
        requireNonNull(unreviewed, "unreviewed");
    }
}
