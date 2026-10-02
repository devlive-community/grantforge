// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.service.agent.AgentTokenView;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An agent token; its secret is only ever sent when it is issued.
 *
 * @param id the token's id
 * @param serviceId the service whose agents use it
 * @param name what it is for
 * @param hint its first characters
 * @param createdAt when it was issued
 * @param expiresAt when it stops working
 * @param revokedAt when it was revoked
 * @param lastUsedAt when an agent last signed in with it
 * @param usable whether it signs agents in now
 */
public record AgentTokenResponse(String id, String serviceId, String name, String hint, Instant createdAt, @Nullable Instant expiresAt,
        @Nullable Instant revokedAt, @Nullable Instant lastUsedAt, boolean usable)
{
    /**
     * Converts a view.
     *
     * @param view the view
     * @return the response
     */
    public static AgentTokenResponse from(AgentTokenView view)
    {
        return new AgentTokenResponse(Long.toString(view.id()), Long.toString(view.serviceId()), view.name(), view.hint(), view.createdAt(),
                view.expiresAt(), view.revokedAt(), view.lastUsedAt(), view.usable());
    }
}
