// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An agent as the console lists it.
 *
 * @param id the registration's id
 * @param serviceId the service
 * @param instance the name it gives itself
 * @param host where it runs
 * @param agentVersion its version
 * @param appliedPolicyVersion the policy version it applies
 * @param clientIp where its last heartbeat came from
 * @param lastSeenAt when it last reported
 * @param status how it is doing
 */
public record AgentView(long id, long serviceId, String instance, @Nullable String host, @Nullable String agentVersion,
        @Nullable Long appliedPolicyVersion, @Nullable String clientIp, Instant lastSeenAt, AgentStatus status)
{
}
