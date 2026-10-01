// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.org;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A new department, or new details of one (the parent is then ignored; moving has its own call).
 *
 * @param parentId the parent department, or {@code null} for a root
 * @param code the code, unique in the tenant: lowercase letters, digits, dots, hyphens or underscores
 * @param name the display name
 */
public record OrgUnitRequest(
        @Size(max = 20) @Nullable String parentId,
        @NotBlank @Size(max = 64) @Nullable String code,
        @NotBlank @Size(max = 128) @Nullable String name)
{
}
