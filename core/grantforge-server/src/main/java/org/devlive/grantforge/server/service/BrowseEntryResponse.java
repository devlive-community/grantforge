// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import org.devlive.grantforge.plugin.api.BrowseEntry;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * One entry of a directory of a service.
 *
 * @param name its own name
 * @param value what a policy names to mean it
 * @param directory whether it can be browsed into
 * @param owner the owning user, if known
 * @param group the owning group, if known
 * @param permission the permissions as the target system writes them, if known
 * @param size the size in bytes, if known
 * @param modifiedAt when it last changed, if known
 */
public record BrowseEntryResponse(String name, String value, boolean directory, @Nullable String owner, @Nullable String group,
        @Nullable String permission, @Nullable Long size, @Nullable Instant modifiedAt)
{
    static BrowseEntryResponse from(BrowseEntry entry)
    {
        return new BrowseEntryResponse(entry.name(), entry.value(), entry.directory(), entry.owner(), entry.group(), entry.permission(),
                entry.size(), entry.modifiedAt());
    }
}
