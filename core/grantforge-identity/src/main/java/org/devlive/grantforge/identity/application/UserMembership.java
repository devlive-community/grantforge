// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import static java.util.Objects.requireNonNull;

/**
 * A department an account belongs to.
 *
 * @param unitId the department
 * @param unitName the department's name
 * @param primary whether it is the account's primary department
 */
public record UserMembership(long unitId, String unitName, boolean primary)
{
    /** Validates the values. */
    public UserMembership
    {
        requireNonNull(unitName, "unitName");
    }
}
