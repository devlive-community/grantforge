// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.position;

import org.devlive.grantforge.identity.domain.PositionRow;
import org.jspecify.annotations.Nullable;

/**
 * A position; the ID is a string because it exceeds JavaScript's safe integers.
 *
 * @param id the position ID
 * @param code the code, unique in the tenant
 * @param name the name
 * @param description the description, if any
 * @param sortOrder where it appears in lists, smallest first
 * @param holders how many accounts hold it
 */
public record PositionResponse(String id, String code, String name, @Nullable String description, int sortOrder,
        long holders)
{
    /**
     * Converts a row.
     *
     * @param position the row
     * @return the response
     */
    public static PositionResponse from(PositionRow position)
    {
        return new PositionResponse(Long.toString(position.id()), position.code(), position.name(),
                position.description(), position.sortOrder(), position.holders());
    }
}
