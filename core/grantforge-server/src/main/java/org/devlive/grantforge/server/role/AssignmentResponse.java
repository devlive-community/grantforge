// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import org.devlive.grantforge.authz.application.AssignmentView;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An assignment of a role; IDs are strings because they exceed JavaScript's safe integers.
 *
 * @param id the assignment ID
 * @param roleId the role
 * @param subjectType what the subject is
 * @param subjectId the subject's ID
 * @param subjectName the subject's display name
 * @param subjectDetail the subject's login name or code, or {@code null}
 * @param validFrom when it starts, or {@code null}
 * @param validTo when it ends, or {@code null}
 * @param includeSubUnits for a department: whether its sub-departments are included
 * @param valid whether it applies now
 */
public record AssignmentResponse(String id, String roleId, SubjectType subjectType, String subjectId, String subjectName,
        @Nullable String subjectDetail, @Nullable Instant validFrom, @Nullable Instant validTo, boolean includeSubUnits,
        boolean valid)
{
    /**
     * Converts a view.
     *
     * @param assignment the view
     * @return the response
     */
    public static AssignmentResponse from(AssignmentView assignment)
    {
        return new AssignmentResponse(Long.toString(assignment.id()), Long.toString(assignment.roleId()),
                assignment.subject().type(), Long.toString(assignment.subject().id()), assignment.subject().name(),
                assignment.subject().detail(), assignment.terms().validFrom(), assignment.terms().validTo(),
                assignment.terms().includeSubUnits(), assignment.valid());
    }
}
