// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A new user group, or new details of one.
 *
 * @param code the code, unique in the tenant: lowercase letters, digits, dots, hyphens or underscores
 * @param name the name
 * @param description what the group is for; blank clears it
 */
public record GroupRequest(
        @NotBlank @Size(max = 64) @Nullable String code,
        @NotBlank @Size(max = 128) @Nullable String name,
        @Size(max = 512) @Nullable String description)
{
}
