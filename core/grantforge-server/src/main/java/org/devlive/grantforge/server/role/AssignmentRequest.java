// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Who gets a role, and how long and how widely.
 *
 * @param subjectType what the subject is
 * @param subjectId the subject's ID
 * @param validFrom when it starts, or {@code null} for at once
 * @param validTo when it ends (exclusive), or {@code null} for never
 * @param includeSubUnits for a department: whether members of its sub-departments have the role too
 */
public record AssignmentRequest(
        @NotNull @Nullable SubjectType subjectType,
        @NotBlank @Size(max = 20) @Nullable String subjectId,
        @Nullable Instant validFrom,
        @Nullable Instant validTo,
        @Nullable Boolean includeSubUnits)
{
    /**
     * Returns the terms.
     *
     * @return the terms
     */
    RoleAssignment.Terms terms()
    {
        return new AssignmentTermsRequest(validFrom, validTo, includeSubUnits).terms();
    }
}
