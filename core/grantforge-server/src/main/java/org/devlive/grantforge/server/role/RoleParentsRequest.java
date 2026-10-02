// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.server.web.PathIds;

import java.util.List;

/**
 * The roles a role is to inherit from directly, replacing the ones it inherits from now.
 *
 * @param parentIds role IDs, at most 20; empty to inherit from nothing
 */
public record RoleParentsRequest(@NotNull @Size(max = 20) List<@NotNull @Size(max = 20) String> parentIds)
{
    /** Copies the IDs; JSON without them gives an empty list. */
    @SuppressWarnings("ConstantValue")
    public RoleParentsRequest
    {
        parentIds = parentIds == null ? List.of() : List.copyOf(parentIds);
    }

    /**
     * Parses the IDs.
     *
     * @return the role IDs
     */
    public List<Long> ids()
    {
        return parentIds.stream().map(id -> PathIds.parse(id.trim(), "role")).toList();
    }
}
