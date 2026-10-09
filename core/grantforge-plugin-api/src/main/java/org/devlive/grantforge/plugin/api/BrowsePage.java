// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * One page of a directory's entries. A plugin either pages through the whole directory or, when the target system
 * cannot page and the directory is too large to read at once, fails with {@link LookupException.Reason#LIMIT_EXCEEDED}:
 * a page never silently leaves entries out.
 *
 * @param root the directory browsing starts from and may not leave, such as the configured lookup directory
 * @param directory the directory listed
 * @param entries its entries, in the order the plugin keeps from page to page
 * @param nextCursor where the next page starts; {@code null} when this is the last page
 * @since 1.1.0
 */
public record BrowsePage(String root, String directory, List<BrowseEntry> entries, @Nullable String nextCursor)
{
    /** Checks and copies the values. */
    public BrowsePage
    {
        requireNonNull(root, "root");
        requireNonNull(directory, "directory");
        entries = List.copyOf(requireNonNull(entries, "entries"));
    }
}
