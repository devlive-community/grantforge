// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import org.devlive.grantforge.plugin.api.BrowsePage;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One page of a directory of a service.
 *
 * @param root the directory browsing starts from and may not leave
 * @param directory the directory listed
 * @param entries its entries
 * @param nextCursor where the next page starts; {@code null} on the last page
 */
public record BrowsePageResponse(String root, String directory, List<BrowseEntryResponse> entries, @Nullable String nextCursor)
{
    /** Copies the entries. */
    public BrowsePageResponse
    {
        entries = List.copyOf(entries);
    }

    /**
     * Converts a page.
     *
     * @param page the page a plugin listed
     * @return the response
     */
    public static BrowsePageResponse from(BrowsePage page)
    {
        return new BrowsePageResponse(page.root(), page.directory(), page.entries().stream().map(BrowseEntryResponse::from).toList(),
                page.nextCursor());
    }
}
