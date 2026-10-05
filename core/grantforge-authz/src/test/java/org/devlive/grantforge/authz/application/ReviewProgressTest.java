// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewProgressTest
{
    @Test
    void countsTheItems()
    {
        assertThat(ReviewProgress.NONE.total()).isZero();
        assertThat(new ReviewProgress(5, 1, 2, 2, 1)).extracting(ReviewProgress::pending, ReviewProgress::keep, ReviewProgress::revoke,
                ReviewProgress::revoked).containsExactly(1L, 2L, 2L, 1L);
    }
}
