// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * An account with all its departments.
 *
 * @param summary the account
 * @param memberships its departments, the primary one first
 * @param positions the positions it holds, in list order
 */
public record UserDetail(UserSummary summary, List<UserMembership> memberships, List<UserPosition> positions)
{
    /** Copies the memberships and positions. */
    public UserDetail
    {
        requireNonNull(summary, "summary");
        memberships = List.copyOf(requireNonNull(memberships, "memberships"));
        positions = List.copyOf(requireNonNull(positions, "positions"));
    }
}
