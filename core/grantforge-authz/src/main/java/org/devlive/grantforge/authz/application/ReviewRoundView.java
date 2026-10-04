// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ReviewRoundStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * A round of an access review as the console shows it.
 *
 * @param id the ID
 * @param reviewId the review
 * @param status where it stands
 * @param startedAt when it started
 * @param dueAt when it completes by itself
 * @param startedBy who started it, or {@code null} for the schedule
 * @param endedAt when it ended, or {@code null} while open
 * @param endedBy who ended it, or {@code null} for the schedule or while open
 * @param progress how far it got
 */
public record ReviewRoundView(long id, long reviewId, ReviewRoundStatus status, Instant startedAt, Instant dueAt, @Nullable Subject startedBy,
        @Nullable Instant endedAt, @Nullable Subject endedBy, ReviewProgress progress)
{
    /** Validates the values. */
    public ReviewRoundView
    {
        requireNonNull(status, "status");
        requireNonNull(startedAt, "startedAt");
        requireNonNull(dueAt, "dueAt");
        requireNonNull(progress, "progress");
    }
}
