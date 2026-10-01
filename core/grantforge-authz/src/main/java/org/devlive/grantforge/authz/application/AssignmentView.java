// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.RoleAssignment;

import static java.util.Objects.requireNonNull;

/**
 * An assignment of a role.
 *
 * @param id the assignment ID
 * @param roleId the role
 * @param subject who has the role
 * @param terms validity and reach
 * @param valid whether it applies now
 */
public record AssignmentView(long id, long roleId, Subject subject, RoleAssignment.Terms terms, boolean valid)
{
    /** Validates the values. */
    public AssignmentView
    {
        requireNonNull(subject, "subject");
        requireNonNull(terms, "terms");
    }
}
