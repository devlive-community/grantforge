// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.jspecify.annotations.Nullable;

/**
 * Filters for listing accounts; {@code null} means no filter.
 *
 * @param text a lowercase text the login name, display name or e-mail address contains
 * @param state whether the accounts can sign in right now
 * @param orgUnitPath the path of a department whose members (and members of its sub-departments) to list
 * @param orgUnitId the department whose own members to list; ignored when {@code orgUnitPath} is set
 */
public record UserCriteria(@Nullable String text, @Nullable UserState state, @Nullable String orgUnitPath,
        @Nullable Long orgUnitId)
{
    /** No filter. */
    public static final UserCriteria ALL = new UserCriteria(null, null, null, null);
}
