// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.identity.domain.UserState;
import org.jspecify.annotations.Nullable;

/**
 * What administrators filter the user list by; {@code null} means no filter.
 *
 * @param text text the login name, display name or e-mail address contains
 * @param state whether the accounts can sign in right now
 * @param unitId a department whose members to list
 * @param includeSubUnits whether members of the department's sub-departments are listed too
 */
public record UserFilter(@Nullable String text, @Nullable UserState state, @Nullable Long unitId, boolean includeSubUnits)
{
    /** No filter. */
    public static final UserFilter ALL = new UserFilter(null, null, null, false);
}
