// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.sod;

import org.devlive.grantforge.authz.application.SodConflict;
import org.devlive.grantforge.authz.domain.SodMode;
import org.devlive.grantforge.server.role.RoleResponse;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * An account holding more of a constraint's roles than it allows.
 *
 * @param constraintId the constraint
 * @param constraintName its name
 * @param mode whether the constraint is enforced or only reported
 * @param maxRoles how many of its roles one account may hold
 * @param accountId the account
 * @param accountName the account's display name
 * @param username the account's user name
 * @param roles the constraint's roles the account holds
 */
public record SodConflictResponse(String constraintId, String constraintName, SodMode mode, int maxRoles, String accountId, String accountName,
        @Nullable String username, List<RoleResponse> roles)
{
    /**
     * Converts a conflict.
     *
     * @param conflict the conflict
     * @return the response
     */
    public static SodConflictResponse from(SodConflict conflict)
    {
        return new SodConflictResponse(Long.toString(conflict.constraint().id()), conflict.constraint().name(), conflict.constraint().mode(),
                conflict.constraint().maxRoles(), Long.toString(conflict.account().id()), conflict.account().name(), conflict.account().detail(),
                conflict.roles().stream().map(RoleResponse::from).toList());
    }
}
