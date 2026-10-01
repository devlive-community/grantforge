// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A new application.
 *
 * @param code the code, unique among applications: a lowercase letter, then lowercase letters, digits, dots,
 *        hyphens or underscores
 * @param name the display name
 * @param description an optional explanation
 */
public record ApplicationRequest(
        @NotBlank @Size(max = 64) @Nullable String code,
        @NotBlank @Size(max = 128) @Nullable String name,
        @Size(max = 500) @Nullable String description)
{
}
