// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.jspecify.annotations.Nullable;

/**
 * Whom and what to preview a role's data policies for.
 *
 * @param accountId the user
 * @param entityCode the secured entity
 * @param action the action
 */
public record DataPreviewRequest(
        @NotNull @Pattern(regexp = "\\d{1,19}") @Nullable String accountId,
        @NotNull @Size(max = 64) @Nullable String entityCode,
        @NotNull @Nullable DataAction action)
{
}
