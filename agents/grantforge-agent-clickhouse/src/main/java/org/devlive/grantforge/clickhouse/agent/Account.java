// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import java.util.List;
import java.util.Set;

/**
 * A ClickHouse user the agent keeps a role for, as ClickHouse shows it.
 *
 * @param name the user name
 * @param groups the ClickHouse roles the user holds, directly or through other roles, but the agent's own; policies
 *        name them as groups
 * @param granted the roles granted to the user directly, the agent's own included
 * @param defaultAll whether every granted role is a default role, but {@code defaultExcept}
 * @param defaultRoles the default roles, when not all are
 * @param defaultExcept the roles that are no default roles, when all others are
 */
record Account(String name, Set<String> groups, Set<String> granted, boolean defaultAll, List<String> defaultRoles, List<String> defaultExcept)
{
    /** Copies the roles. */
    Account
    {
        groups = Set.copyOf(groups);
        granted = Set.copyOf(granted);
        defaultRoles = List.copyOf(defaultRoles);
        defaultExcept = List.copyOf(defaultExcept);
    }

    /**
     * Returns whether a role the user holds is active when the user signs in.
     *
     * @param role the role
     * @return {@code true} if it is a default role
     */
    boolean isDefault(String role)
    {
        return defaultAll ? !defaultExcept.contains(role) : defaultRoles.contains(role);
    }
}
