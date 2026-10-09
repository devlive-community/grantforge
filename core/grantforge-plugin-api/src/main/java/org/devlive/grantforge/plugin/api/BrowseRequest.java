// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Asks a plugin for one page of a directory's entries, for the path picker of the policy editor.
 *
 * @param config the service's configuration
 * @param resource the level being browsed, which declares {@code browseSupported}
 * @param directory the directory to list; empty for the plugin's starting directory
 * @param cursor where the previous page ended, as that page said; {@code null} for the first page
 * @param pageSize the most entries wanted, one through {@value #MAX_PAGE_SIZE}
 * @since 1.1.0
 */
public record BrowseRequest(ServiceConfig config, String resource, String directory, @Nullable String cursor, int pageSize)
{
    /** The largest page a plugin is asked for. */
    public static final int MAX_PAGE_SIZE = 500;

    /**
     * Checks the values.
     *
     * @throws IllegalArgumentException if the page size is outside one through {@value #MAX_PAGE_SIZE}
     */
    public BrowseRequest
    {
        requireNonNull(config, "config");
        requireNonNull(resource, "resource");
        requireNonNull(directory, "directory");
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
