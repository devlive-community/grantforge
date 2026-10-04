// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * The reader of data, as data scopes and condition variables see them.
 *
 * @param accountId the account
 * @param tenantId its tenant
 * @param username its user name
 * @param orgUnitIds the departments it is a member of
 * @param orgUnitPaths the materialised paths of those departments, to reach their sub-departments
 * @param groupCodes the codes of its groups
 * @param positionCodes the codes of its positions
 */
public record DataSubject(long accountId, long tenantId, String username, List<Long> orgUnitIds, List<String> orgUnitPaths,
        List<String> groupCodes, List<String> positionCodes)
{
    /** Checks and copies the values. */
    public DataSubject
    {
        requireNonNull(username, "username");
        orgUnitIds = List.copyOf(orgUnitIds);
        orgUnitPaths = List.copyOf(orgUnitPaths);
        groupCodes = List.copyOf(groupCodes);
        positionCodes = List.copyOf(positionCodes);
    }
}
