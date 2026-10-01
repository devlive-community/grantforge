// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * A user group with its number of members, read in one query.
 *
 * @param id the group ID
 * @param code the code
 * @param name the name
 * @param description the description, if any
 * @param members how many accounts belong to it
 * @param createdAt when it was created
 */
public record GroupRow(long id, String code, String name, @Nullable String description, long members, Instant createdAt)
{
    /** Validates the values. */
    public GroupRow
    {
        requireNonNull(code, "code");
        requireNonNull(name, "name");
        requireNonNull(createdAt, "createdAt");
    }
}
