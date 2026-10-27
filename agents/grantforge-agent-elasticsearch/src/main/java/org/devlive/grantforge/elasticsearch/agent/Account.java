// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import java.util.Set;

/**
 * An Elasticsearch user the agent keeps a role for: a native user, a user the policies name, or one the agent kept a
 * role for before, who may sign in through a realm the agent cannot list, such as LDAP or SAML.
 *
 * @param name the user name
 * @param groups the roles a native user holds besides the agent's, which policies name as groups; none for other users,
 *        whose groups the snapshot gives
 * @param internal whether the native realm holds the user
 */
record Account(String name, Set<String> groups, boolean internal)
{
    /** Copies the groups. */
    Account
    {
        groups = Set.copyOf(groups);
    }
}
