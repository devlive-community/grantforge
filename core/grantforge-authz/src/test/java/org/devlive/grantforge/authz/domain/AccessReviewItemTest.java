// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessReviewItemTest
{
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    private static AccessReviewItem item()
    {
        RoleAssignment assignment = RoleAssignment.create(3, SubjectType.GROUP, 8, RoleAssignment.Terms.UNLIMITED);
        ReflectionTestUtils.setField(assignment, "id", 42L);
        return AccessReviewItem.of(5, assignment);
    }

    @Test
    void copiesTheAssignment()
    {
        AccessReviewItem item = item();

        assertThat(item).extracting(AccessReviewItem::getRoundId, AccessReviewItem::getAssignmentId, AccessReviewItem::getRoleId,
                AccessReviewItem::getSubjectType, AccessReviewItem::getSubjectId, AccessReviewItem::getDecision, AccessReviewItem::getOutcome)
                .containsExactly(5L, 42L, 3L, SubjectType.GROUP, 8L, ReviewDecision.PENDING, null);
    }

    @Test
    void decisionsChangeUntilApplied()
    {
        AccessReviewItem item = item();
        item.decide(ReviewDecision.REVOKE, 9, NOW, "left the team");
        assertThat(item).extracting(AccessReviewItem::getDecision, AccessReviewItem::getDecidedBy, AccessReviewItem::getDecidedAt,
                AccessReviewItem::getComment).containsExactly(ReviewDecision.REVOKE, 9L, NOW, "left the team");

        // Taking the decision back forgets who made it.
        item.decide(ReviewDecision.PENDING, 9, NOW, "ignored");
        assertThat(item).extracting(AccessReviewItem::getDecision, AccessReviewItem::getDecidedBy, AccessReviewItem::getDecidedAt,
                AccessReviewItem::getComment).containsExactly(ReviewDecision.PENDING, null, null, null);

        item.apply(ReviewOutcome.KEPT);
        assertThat(item.getOutcome()).isEqualTo(ReviewOutcome.KEPT);
        assertThatThrownBy(() -> item.decide(ReviewDecision.KEEP, 9, NOW, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> item.apply(ReviewOutcome.REVOKED)).isInstanceOf(IllegalStateException.class);
    }
}
