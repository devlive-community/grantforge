// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import static java.util.Objects.requireNonNull;

/**
 * A role accounts may ask for.
 *
 * @param role the role
 * @param maxDays the longest period one may ask for
 */
public record RequestableRoleView(RoleView role, int maxDays)
{
    /** Checks the role. */
    public RequestableRoleView
    {
        requireNonNull(role, "role");
    }
}
