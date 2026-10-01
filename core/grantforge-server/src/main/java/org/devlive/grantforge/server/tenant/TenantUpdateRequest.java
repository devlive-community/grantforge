// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * New details of a tenant; the code never changes.
 *
 * @param name the display name
 */
public record TenantUpdateRequest(@NotBlank @Size(max = 128) @Nullable String name)
{
}
