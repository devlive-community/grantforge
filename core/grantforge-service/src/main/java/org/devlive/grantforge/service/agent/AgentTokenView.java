// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An agent token as the console lists it; the token itself is never shown again after it was issued.
 *
 * @param id the token's id
 * @param serviceId the service whose agents use it
 * @param name what it is for
 * @param hint its first characters
 * @param createdAt when it was issued
 * @param expiresAt when it stops working, or {@code null} for never
 * @param revokedAt when it was revoked, or {@code null}
 * @param lastUsedAt when an agent last signed in with it, or {@code null}
 * @param usable whether it signs agents in now
 */
public record AgentTokenView(long id, long serviceId, String name, String hint, Instant createdAt, @Nullable Instant expiresAt,
        @Nullable Instant revokedAt, @Nullable Instant lastUsedAt, boolean usable)
{
}
