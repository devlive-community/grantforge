// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import java.util.Set;

/**
 * A PostgreSQL user the agent keeps a role for, as PostgreSQL shows it.
 *
 * @param name the user name
 * @param groups the roles the user is a member of, directly or through other roles, but the agent's own; policies name
 *        them as groups
 * @param granted the roles the user is a member of directly, the agent's own included
 * @param inheriting those of the granted roles whose privileges the user has without {@code SET ROLE}
 */
record Account(String name, Set<String> groups, Set<String> granted, Set<String> inheriting)
{
    /** Copies the roles. */
    Account
    {
        groups = Set.copyOf(groups);
        granted = Set.copyOf(granted);
        inheriting = Set.copyOf(inheriting);
    }
}
