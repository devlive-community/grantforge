// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.position;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A new position, or new details of one.
 *
 * @param code the code, unique in the tenant: lowercase letters, digits, dots, hyphens or underscores
 * @param name the name
 * @param description what the position does; blank clears it
 * @param sortOrder where it appears in lists, 0 if omitted
 */
public record PositionRequest(
        @NotBlank @Size(max = 64) @Nullable String code,
        @NotBlank @Size(max = 128) @Nullable String name,
        @Size(max = 512) @Nullable String description,
        @Min(0) @Max(1_000_000) @Nullable Integer sortOrder)
{
    /**
     * Returns the sort order, 0 when omitted.
     *
     * @return the sort order
     */
    public int order()
    {
        Integer value = sortOrder;
        return value == null ? 0 : value;
    }
}
