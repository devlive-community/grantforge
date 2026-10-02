// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Changes to a role's grants on one application.
 *
 * @param applicationId the application the resources belong to
 * @param changes the changes, at most 1000; the last change of a resource wins
 */
public record GrantChangesRequest(@NotBlank @Size(max = 20) @Nullable String applicationId,
        @Size(max = 1000) List<@Valid GrantChangeRequest> changes)
{
    /** Copies the changes; JSON without them gives an empty list. */
    @SuppressWarnings("ConstantValue")
    public GrantChangesRequest
    {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
