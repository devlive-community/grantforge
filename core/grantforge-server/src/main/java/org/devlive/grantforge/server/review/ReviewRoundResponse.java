// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.review;

import org.devlive.grantforge.authz.application.ReviewProgress;
import org.devlive.grantforge.authz.application.ReviewRoundView;
import org.devlive.grantforge.authz.application.Subject;
import org.devlive.grantforge.authz.domain.ReviewRoundStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A round of an access review.
 *
 * @param id the ID
 * @param reviewId the review
 * @param status where it stands
 * @param startedAt when it started
 * @param dueAt when it completes by itself
 * @param startedByName who started it; left out for the schedule
 * @param endedAt when it ended
 * @param endedByName who ended it; left out for the schedule
 * @param progress how far it got
 */
public record ReviewRoundResponse(String id, String reviewId, ReviewRoundStatus status, Instant startedAt, Instant dueAt, @Nullable String startedByName,
        @Nullable Instant endedAt, @Nullable String endedByName, Progress progress)
{
    /**
     * Converts a view.
     *
     * @param view the round
     * @return the response
     */
    public static ReviewRoundResponse from(ReviewRoundView view)
    {
        ReviewProgress counts = view.progress();
        return new ReviewRoundResponse(Long.toString(view.id()), Long.toString(view.reviewId()), view.status(), view.startedAt(), view.dueAt(),
                name(view.startedBy()), view.endedAt(), name(view.endedBy()),
                new Progress(counts.total(), counts.pending(), counts.keep(), counts.revoke(), counts.revoked()));
    }

    private static @Nullable String name(@Nullable Subject subject)
    {
        return subject == null ? null : subject.name();
    }

    /**
     * How far a round got.
     *
     * @param total the assignments under review
     * @param pending those nobody decided about
     * @param keep those to keep
     * @param revoke those to revoke
     * @param revoked those completing the round removed
     */
    public record Progress(long total, long pending, long keep, long revoke, long revoked)
    {
    }
}
