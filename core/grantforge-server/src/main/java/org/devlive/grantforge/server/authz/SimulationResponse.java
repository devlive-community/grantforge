// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

import org.devlive.grantforge.authz.application.SimulationResult;

import java.util.List;

/**
 * What an account would gain and lose if some changes were made.
 *
 * @param accountId the account
 * @param rolesBefore the roles in effect now, inherited ones included
 * @param rolesAfter the roles that would be in effect
 * @param gainedResources console resources it could use only after the changes
 * @param lostResources console resources it could use only before the changes
 * @param gainedPermissions API permissions it would hold only after the changes
 * @param lostPermissions API permissions it holds only before the changes
 */
public record SimulationResponse(String accountId, List<String> rolesBefore, List<String> rolesAfter,
        List<EffectiveAccessResponse.Item> gainedResources, List<EffectiveAccessResponse.Item> lostResources,
        List<EffectiveAccessResponse.Item> gainedPermissions, List<EffectiveAccessResponse.Item> lostPermissions)
{
    /** Copies the lists. */
    public SimulationResponse
    {
        rolesBefore = List.copyOf(rolesBefore);
        rolesAfter = List.copyOf(rolesAfter);
        gainedResources = List.copyOf(gainedResources);
        lostResources = List.copyOf(lostResources);
        gainedPermissions = List.copyOf(gainedPermissions);
        lostPermissions = List.copyOf(lostPermissions);
    }

    /**
     * Converts a result.
     *
     * @param accountId the account
     * @param result the result
     * @return the response
     */
    public static SimulationResponse from(long accountId, SimulationResult result)
    {
        return new SimulationResponse(Long.toString(accountId), result.rolesBefore(), result.rolesAfter(),
                result.gainedResources().stream().map(EffectiveAccessResponse.Item::from).toList(),
                result.lostResources().stream().map(EffectiveAccessResponse.Item::from).toList(),
                result.gainedPermissions().stream().map(EffectiveAccessResponse.Item::from).toList(),
                result.lostPermissions().stream().map(EffectiveAccessResponse.Item::from).toList());
    }
}
