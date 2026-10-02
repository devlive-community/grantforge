// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.service.agent.AgentStatus;
import org.devlive.grantforge.service.agent.AgentView;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An agent of a service.
 *
 * @param id the registration's id
 * @param instance the name it gives itself
 * @param host where it runs
 * @param agentVersion its version
 * @param appliedPolicyVersion the policy version it applies
 * @param clientIp where its last heartbeat came from
 * @param lastSeenAt when it last reported
 * @param status how it is doing
 */
public record AgentResponse(String id, String instance, @Nullable String host, @Nullable String agentVersion,
        @Nullable Long appliedPolicyVersion, @Nullable String clientIp, Instant lastSeenAt, AgentStatus status)
{
    /**
     * Converts a view.
     *
     * @param view the view
     * @return the response
     */
    public static AgentResponse from(AgentView view)
    {
        return new AgentResponse(Long.toString(view.id()), view.instance(), view.host(), view.agentVersion(), view.appliedPolicyVersion(),
                view.clientIp(), view.lastSeenAt(), view.status());
    }
}
