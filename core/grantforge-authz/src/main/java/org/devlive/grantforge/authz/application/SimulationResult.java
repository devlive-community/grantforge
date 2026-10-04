// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import java.util.List;

/**
 * What an account would gain and lose if the changes of a {@link Simulation} were made.
 *
 * @param rolesBefore the codes of the roles in effect now, inherited ones included
 * @param rolesAfter the codes of the roles that would be in effect
 * @param gainedResources console resources it could use only after the changes
 * @param lostResources console resources it could use only before the changes
 * @param gainedPermissions API permissions it would hold only after the changes
 * @param lostPermissions API permissions it holds only before the changes
 */
public record SimulationResult(List<String> rolesBefore, List<String> rolesAfter, List<EffectiveAccess.Item> gainedResources,
        List<EffectiveAccess.Item> lostResources, List<EffectiveAccess.Item> gainedPermissions, List<EffectiveAccess.Item> lostPermissions)
{
    /** Copies the lists. */
    public SimulationResult
    {
        rolesBefore = List.copyOf(rolesBefore);
        rolesAfter = List.copyOf(rolesAfter);
        gainedResources = List.copyOf(gainedResources);
        lostResources = List.copyOf(lostResources);
        gainedPermissions = List.copyOf(gainedPermissions);
        lostPermissions = List.copyOf(lostPermissions);
    }
}
