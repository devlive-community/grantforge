// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.access;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A request for a role.
 *
 * @param roleId the role
 * @param reason why, at most 500 characters
 * @param days for how many days
 */
public record AccessRequestBody(@NotBlank @Size(max = 20) @Nullable String roleId, @NotBlank @Size(max = 500) @Nullable String reason,
        @NotNull @Nullable Integer days)
{
}
