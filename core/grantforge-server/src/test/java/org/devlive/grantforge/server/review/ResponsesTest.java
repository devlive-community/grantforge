// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.review;

import org.devlive.grantforge.authz.application.AccessReviewView;
import org.devlive.grantforge.authz.application.ReviewItemView;
import org.devlive.grantforge.authz.application.ReviewProgress;
import org.devlive.grantforge.authz.application.ReviewRoundView;
import org.devlive.grantforge.authz.application.RoleView;
import org.devlive.grantforge.authz.application.Subject;
import org.devlive.grantforge.authz.domain.ReviewDecision;
import org.devlive.grantforge.authz.domain.ReviewFallback;
import org.devlive.grantforge.authz.domain.ReviewOutcome;
import org.devlive.grantforge.authz.domain.ReviewRoundStatus;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResponsesTest
{
    private static final RoleView REPORTS = new RoleView(3, "reports", "Reports", null, RoleType.CUSTOM, true);
    private static final Subject BOSS = new Subject(SubjectType.USER, 9, "Boss", "boss");

    @Test
    void convertsReviewsAndRounds()
    {
        ReviewRoundView round = new ReviewRoundView(5, 4, ReviewRoundStatus.OPEN, Instant.EPOCH, Instant.EPOCH.plusSeconds(60), BOSS, null, null,
                new ReviewProgress(3, 1, 1, 1, 0));
        AccessReviewResponse review = AccessReviewResponse.from(new AccessReviewView(4, "Quarterly", null, List.of(REPORTS), 7, 90, ReviewFallback.KEEP,
                true, null, Instant.EPOCH, round));

        assertThat(review).extracting(AccessReviewResponse::id, AccessReviewResponse::name, AccessReviewResponse::intervalDays)
                .containsExactly("4", "Quarterly", 90);
        assertThat(review.roles()).extracting(role -> role.id()).containsExactly("3");
        assertThat(review.openRound()).isNotNull().extracting(ReviewRoundResponse::id, ReviewRoundResponse::reviewId, ReviewRoundResponse::startedByName,
                ReviewRoundResponse::endedByName).containsExactly("5", "4", "Boss", null);
        assertThat(review.openRound()).extracting(ReviewRoundResponse::progress).isEqualTo(new ReviewRoundResponse.Progress(3, 1, 1, 1, 0));
        assertThat(AccessReviewResponse.from(new AccessReviewView(4, "Q", null, List.of(), 7, null, ReviewFallback.KEEP, false, null, null, null))
                .openRound()).isNull();
    }

    @Test
    void convertsItems()
    {
        ReviewItemResponse kept = ReviewItemResponse.from(new ReviewItemView(8, REPORTS, new Subject(SubjectType.ORG_UNIT, 6, "Sales", "sales"),
                true, null, Instant.EPOCH, true, ReviewDecision.KEEP, BOSS, Instant.EPOCH, "fine", ReviewOutcome.KEPT));
        assertThat(kept).extracting(ReviewItemResponse::id, ReviewItemResponse::subjectId, ReviewItemResponse::assigned, ReviewItemResponse::validTo,
                ReviewItemResponse::includeSubUnits, ReviewItemResponse::decidedByName)
                .containsExactly("8", "6", true, Instant.EPOCH, true, "Boss");

        ReviewItemResponse gone = ReviewItemResponse.from(new ReviewItemView(8, REPORTS, new Subject(SubjectType.USER, 7, "7", null), false, null, null,
                false, ReviewDecision.PENDING, null, null, null, null));
        assertThat(gone).extracting(ReviewItemResponse::assigned, ReviewItemResponse::validFrom, ReviewItemResponse::includeSubUnits,
                ReviewItemResponse::decidedByName).containsExactly(false, null, false, null);
    }
}
