// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.access;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.server.web.PathIds;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The roles that may be asked for, replacing the current ones.
 *
 * @param roles each role with its longest period
 */
public record RequestableRolesBody(@NotNull @Size(max = 200) @Nullable List<@Valid @NotNull Entry> roles)
{
    /**
     * Returns the longest period of each role.
     *
     * @return days by role
     */
    public Map<Long, Integer> maxDays()
    {
        Map<Long, Integer> days = new LinkedHashMap<>();
        for (Entry entry : roles == null ? List.<Entry>of() : roles) {
            days.put(PathIds.parse(String.valueOf(entry.roleId()).strip(), "role"), entry.maxDays() == null ? 0 : entry.maxDays());
        }
        return days;
    }

    /**
     * A requestable role.
     *
     * @param roleId the role
     * @param maxDays the longest period one may ask for
     */
    public record Entry(@NotBlank @Size(max = 20) @Nullable String roleId, @NotNull @Nullable Integer maxDays)
    {
    }
}
