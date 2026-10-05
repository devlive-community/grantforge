// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.review;

import org.devlive.grantforge.authz.application.AccessReviewView;
import org.devlive.grantforge.authz.application.ReviewRoundView;
import org.devlive.grantforge.authz.domain.ReviewFallback;
import org.devlive.grantforge.server.role.RoleResponse;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * An access review.
 *
 * @param id the ID
 * @param name the name
 * @param description the explanation, if any
 * @param roles the roles whose assignments are reviewed, by name
 * @param durationDays how long each round stays open
 * @param intervalDays days between scheduled rounds; left out if rounds do not repeat
 * @param unreviewed what happens to undecided assignments
 * @param enabled whether rounds start on schedule
 * @param nextRunAt when the next scheduled round starts
 * @param lastStartedAt when the last round started
 * @param openRound the round reviewers work on now
 */
public record AccessReviewResponse(String id, String name, @Nullable String description, List<RoleResponse> roles, int durationDays,
        @Nullable Integer intervalDays, ReviewFallback unreviewed, boolean enabled, @Nullable Instant nextRunAt, @Nullable Instant lastStartedAt,
        @Nullable ReviewRoundResponse openRound)
{
    /** Copies the roles. */
    public AccessReviewResponse
    {
        roles = List.copyOf(roles);
    }

    /**
     * Converts a view.
     *
     * @param view the review
     * @return the response
     */
    public static AccessReviewResponse from(AccessReviewView view)
    {
        ReviewRoundView open = view.openRound();
        return new AccessReviewResponse(Long.toString(view.id()), view.name(), view.description(), view.roles().stream().map(RoleResponse::from).toList(),
                view.durationDays(), view.intervalDays(), view.unreviewed(), view.enabled(), view.nextRunAt(), view.lastStartedAt(),
                open == null ? null : ReviewRoundResponse.from(open));
    }
}
