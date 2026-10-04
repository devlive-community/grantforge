// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import static java.util.Objects.requireNonNull;

/**
 * A role an account may ask for, with where the account stands.
 *
 * @param role the role
 * @param maxDays the longest period one may ask for
 * @param held whether the account holds the role already
 * @param pending whether the account asked for it and waits for a decision
 */
public record RequestOption(RoleView role, int maxDays, boolean held, boolean pending)
{
    /** Checks the role. */
    public RequestOption
    {
        requireNonNull(role, "role");
    }
}
