// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** The members of the roles and groups of the bound tenant that policies name, for snapshots. */
public interface SnapshotSubjects
{
    /**
     * Returns who holds roles now.
     *
     * @param roleCodes the roles
     * @param now the current time, for the validity of assignments
     * @return the sorted user names of the active accounts holding each role, directly or through groups, departments,
     *         positions or roles inheriting from it; unknown and disabled roles hold no one
     */
    Map<String, List<String>> roleHolders(Collection<String> roleCodes, Instant now);

    /**
     * Returns the members of groups.
     *
     * @param groupCodes the groups
     * @return the sorted user names of the active accounts in each group; unknown groups have no members
     */
    Map<String, List<String>> groupMembers(Collection<String> groupCodes);
}
