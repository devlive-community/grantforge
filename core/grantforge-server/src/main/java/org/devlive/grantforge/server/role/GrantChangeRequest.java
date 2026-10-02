// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.authz.application.GrantChange;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.server.web.PathIds;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * One change to a role's grants.
 *
 * @param resourceId the resource
 * @param effect allow or deny, or {@code null} to take the grant back
 * @param expiresAt when the grant stops applying, or {@code null} for never
 */
public record GrantChangeRequest(@NotBlank @Size(max = 20) @Nullable String resourceId, @Nullable GrantEffect effect,
        @Nullable Instant expiresAt)
{
    /**
     * Returns the change.
     *
     * @return the change
     */
    GrantChange change()
    {
        return new GrantChange(PathIds.parse(String.valueOf(resourceId).trim(), "resource"), effect, expiresAt);
    }
}
