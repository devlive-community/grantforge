// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * One entry of a directory being browsed. Metadata the target system does not have stays {@code null}.
 *
 * @param name the entry's own name, such as {@code alice}
 * @param value what a policy names to mean this entry, such as {@code /user/alice}
 * @param directory whether the entry can be browsed into
 * @param owner the owning user
 * @param group the owning group
 * @param permission the permissions as the target system writes them, such as {@code rwxr-x---}
 * @param size the size in bytes; {@code null} for directories
 * @param modifiedAt when the entry last changed
 * @since 1.1.0
 */
public record BrowseEntry(String name, String value, boolean directory, @Nullable String owner, @Nullable String group,
        @Nullable String permission, @Nullable Long size, @Nullable Instant modifiedAt)
{
    /** Checks the values. */
    public BrowseEntry
    {
        requireNonNull(name, "name");
        requireNonNull(value, "value");
    }
}
