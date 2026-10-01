// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.group;

import org.devlive.grantforge.identity.domain.GroupRow;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A user group; the ID is a string because it exceeds JavaScript's safe integers.
 *
 * @param id the group ID
 * @param code the code, unique in the tenant
 * @param name the name
 * @param description the description, if any
 * @param members how many accounts belong to it
 * @param createdAt when it was created
 */
public record GroupResponse(String id, String code, String name, @Nullable String description, long members,
        Instant createdAt)
{
    /**
     * Converts a row.
     *
     * @param group the row
     * @return the response
     */
    public static GroupResponse from(GroupRow group)
    {
        return new GroupResponse(Long.toString(group.id()), group.code(), group.name(), group.description(),
                group.members(), group.createdAt());
    }
}
