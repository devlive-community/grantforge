// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import java.util.Set;

/**
 * An OpenSearch user the agent keeps a role for: an internal user of the security plugin, a user the policies name, or
 * one the agent kept a role for before, who may sign in through a directory the agent cannot list.
 *
 * @param name the user name
 * @param groups the backend roles of an internal user, which policies name as groups; none for other users, whose
 *        groups the snapshot gives
 * @param internal whether the security plugin's internal user database holds the user
 */
record Account(String name, Set<String> groups, boolean internal)
{
    /** Copies the groups. */
    Account
    {
        groups = Set.copyOf(groups);
    }
}
