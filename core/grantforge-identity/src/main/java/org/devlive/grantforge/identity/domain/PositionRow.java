// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A position with the number of accounts holding it, read in one query.
 *
 * @param id the position ID
 * @param code the code
 * @param name the name
 * @param description the description, if any
 * @param sortOrder where it appears in lists
 * @param holders how many accounts hold it
 */
public record PositionRow(long id, String code, String name, @Nullable String description, int sortOrder, long holders)
{
    /** Validates the values. */
    public PositionRow
    {
        requireNonNull(code, "code");
        requireNonNull(name, "name");
    }
}
