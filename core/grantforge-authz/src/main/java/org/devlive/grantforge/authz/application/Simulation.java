// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Changes to try on an account without making them: roles it would gain or lose, and grants of roles that would change.
 *
 * @param addRoles roles the account would have directly
 * @param removeRoles roles the account would no longer hold, however it holds them now; a role still inherited through another
 *        role it holds stays
 * @param grants grant changes of roles, which apply to every holder
 */
public record Simulation(List<Long> addRoles, List<Long> removeRoles, List<RoleGrantChange> grants)
{
    /** Copies the lists. */
    public Simulation
    {
        addRoles = List.copyOf(addRoles);
        removeRoles = List.copyOf(removeRoles);
        grants = List.copyOf(grants);
    }

    /**
     * A grant change of one role.
     *
     * @param roleId the role
     * @param change the change; an effect of {@code null} takes the grant back
     */
    public record RoleGrantChange(long roleId, GrantChange change)
    {
        /** Checks the change. */
        public RoleGrantChange
        {
            requireNonNull(change, "change");
        }
    }
}
