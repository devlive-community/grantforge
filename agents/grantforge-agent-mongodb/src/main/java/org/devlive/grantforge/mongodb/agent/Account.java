// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A user as policies name it: every MongoDB user of that name, in any database. MongoDB tells {@code alice@admin} from
 * {@code alice@sales}, but a policy allows or denies {@code alice}, and what the name holds is held by each of them.
 *
 * @param name the user name
 * @param identities the MongoDB users of that name
 * @param groups the names of the roles they hold, which policies name as groups, without the agent's roles
 * @param roles the roles they hold outside GrantForge
 */
record Account(String name, List<Identity> identities, Set<String> groups, Set<RoleRef> roles)
{
    /** Copies everything. */
    Account
    {
        identities = List.copyOf(identities);
        groups = Set.copyOf(groups);
        roles = Set.copyOf(roles);
    }

    /**
     * A role, named by its database.
     *
     * @param role the role's name
     * @param database the database it is defined in
     */
    record RoleRef(String role, String database)
    {
        /**
         * Says what the role is, for messages.
         *
         * @return such as {@code readWrite@sales}
         */
        String label()
        {
            return role + "@" + database;
        }
    }

    /**
     * One MongoDB user.
     *
     * @param user its name
     * @param database the database it is defined in
     * @param roles the roles it holds
     */
    record Identity(String user, String database, List<RoleRef> roles)
    {
        /** Copies the roles. */
        Identity
        {
            roles = List.copyOf(roles);
        }

        /**
         * Says what the user is, for messages.
         *
         * @return such as {@code alice@admin}
         */
        String label()
        {
            return user + "@" + database;
        }
    }

    /**
     * Returns the role names a user's identities hold, without repeats.
     *
     * @return them, sorted
     */
    SortedSet<String> groupNames()
    {
        return new TreeSet<>(groups);
    }
}
