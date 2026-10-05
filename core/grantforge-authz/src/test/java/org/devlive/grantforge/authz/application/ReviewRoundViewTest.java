// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ReviewRoundStatus;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewRoundViewTest
{
    @Test
    void exposesItsComponents()
    {
        ReviewRoundView view = new ReviewRoundView(5, 3, ReviewRoundStatus.OPEN, Instant.EPOCH, Instant.EPOCH.plusSeconds(60),
                new Subject(SubjectType.USER, 7, "Boss", "boss"), null, null, ReviewProgress.NONE);

        assertThat(view.reviewId()).isEqualTo(3);
        assertThat(view.startedBy()).extracting(Subject::name).isEqualTo("Boss");
        assertThat(view.endedBy()).isNull();
    }
}
