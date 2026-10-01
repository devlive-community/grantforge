// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * The code and name of a role's copy.
 *
 * @param code the copy's code, unique in the tenant
 * @param name the copy's name
 */
public record RoleCopyRequest(
        @NotBlank @Size(max = 64) @Nullable String code,
        @NotBlank @Size(max = 128) @Nullable String name)
{
}
