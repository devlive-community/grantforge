// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** The users, groups and roles of the bound tenant that policy items may name. */
public interface PolicySubjects
{
    /**
     * Returns which names are not known.
     *
     * @param kind what the names name
     * @param names the names
     * @return those without a user, group or role
     */
    Set<String> unknown(SubjectKind kind, Collection<String> names);

    /**
     * Suggests names containing a text, for completing what a user types.
     *
     * @param kind what to suggest
     * @param text what was typed; blank for any
     * @param limit how many at most
     * @return the names
     */
    List<String> suggest(SubjectKind kind, String text, int limit);
}
