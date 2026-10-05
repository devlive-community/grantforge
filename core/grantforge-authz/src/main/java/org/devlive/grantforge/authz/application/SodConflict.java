// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * An account holding more of a constraint's roles than it allows.
 *
 * @param constraint the constraint
 * @param account the account
 * @param roles the constraint's roles the account holds, by name
 */
public record SodConflict(SodConstraintView constraint, Subject account, List<RoleView> roles)
{
    /** Copies the roles. */
    public SodConflict
    {
        requireNonNull(constraint, "constraint");
        requireNonNull(account, "account");
        roles = List.copyOf(requireNonNull(roles, "roles"));
    }
}
