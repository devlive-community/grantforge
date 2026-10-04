// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.review;

import org.devlive.grantforge.authz.application.ReviewItemView;
import org.devlive.grantforge.authz.application.Subject;
import org.devlive.grantforge.authz.domain.ReviewDecision;
import org.devlive.grantforge.authz.domain.ReviewOutcome;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.server.role.RoleResponse;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An assignment under review.
 *
 * @param id the item ID
 * @param role the role
 * @param subjectType what the subject is
 * @param subjectId the subject's ID
 * @param subjectName the subject's display name; its ID if it was deleted
 * @param subjectDetail the subject's login name or code
 * @param assigned whether the assignment still exists
 * @param validFrom when the assignment starts
 * @param validTo when the assignment ends
 * @param includeSubUnits for a department: whether its sub-departments are included
 * @param decision what the reviewers decided
 * @param decidedByName who decided
 * @param decidedAt when
 * @param comment what the reviewer said
 * @param outcome what completing the round did
 */
public record ReviewItemResponse(String id, RoleResponse role, SubjectType subjectType, String subjectId, String subjectName,
        @Nullable String subjectDetail, boolean assigned, @Nullable Instant validFrom, @Nullable Instant validTo, boolean includeSubUnits,
        ReviewDecision decision, @Nullable String decidedByName, @Nullable Instant decidedAt, @Nullable String comment,
        @Nullable ReviewOutcome outcome)
{
    /**
     * Converts a view.
     *
     * @param view the item
     * @return the response
     */
    public static ReviewItemResponse from(ReviewItemView view)
    {
        Subject subject = view.subject();
        Subject decider = view.decidedBy();
        return new ReviewItemResponse(Long.toString(view.id()), RoleResponse.from(view.role()), subject.type(), Long.toString(subject.id()),
                subject.name(), subject.detail(), view.assigned(), view.validFrom(), view.validTo(), view.includeSubUnits(), view.decision(),
                decider == null ? null : decider.name(), view.decidedAt(), view.comment(), view.outcome());
    }
}
