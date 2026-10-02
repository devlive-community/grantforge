// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * How long and how widely an assignment applies.
 *
 * @param validFrom when it starts, or {@code null} for at once
 * @param validTo when it ends (exclusive), or {@code null} for never
 * @param includeSubUnits for a department: whether members of its sub-departments have the role too; {@code false}
 *        if omitted
 */
public record AssignmentTermsRequest(@Nullable Instant validFrom, @Nullable Instant validTo, @Nullable Boolean includeSubUnits)
{
    /**
     * Returns the terms.
     *
     * @return the terms
     */
    RoleAssignment.Terms terms()
    {
        return new RoleAssignment.Terms(validFrom, validTo, Boolean.TRUE.equals(includeSubUnits));
    }
}
