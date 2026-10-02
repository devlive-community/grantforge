// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.GrantEffect;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * One change to a role's grants.
 *
 * @param resourceId the resource
 * @param effect allow or deny, or {@code null} to take the grant back
 * @param expiresAt when the grant stops applying, or {@code null} for never
 */
public record GrantChange(long resourceId, @Nullable GrantEffect effect, @Nullable Instant expiresAt)
{
}
