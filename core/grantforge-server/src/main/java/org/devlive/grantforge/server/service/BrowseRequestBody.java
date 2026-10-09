// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Which directory of a service to list.
 *
 * @param resource the resource level, which must offer browsing
 * @param directory the directory; the level's starting directory when left out. At most 1024 characters, like a policy
 *        resource value
 * @param cursor where the previous page ended, as that page said; the first page when left out
 * @param pageSize the most entries wanted; 100 unless given, at most 500
 */
public record BrowseRequestBody(
        @NotBlank @Size(max = 64) @Nullable String resource,
        @Size(max = 1024) @Nullable String directory,
        @Size(max = 2048) @Nullable String cursor,
        @Min(1) @Max(500) @Nullable Integer pageSize)
{
}
