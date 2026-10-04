// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccessReviewSchedulerTest
{
    @Test
    void runsTheDueRounds()
    {
        AccessReviewService reviews = mock(AccessReviewService.class);
        when(reviews.runDue()).thenReturn(3, 0);
        AccessReviewScheduler scheduler = new AccessReviewScheduler(reviews);

        scheduler.run();
        scheduler.run();

        verify(reviews, times(2)).runDue();
    }
}
