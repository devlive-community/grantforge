// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.jspecify.annotations.Nullable;

/**
 * What an agent says about itself in a heartbeat.
 *
 * @param instance the name it gives itself, unique within the service, such as {@code namenode-1:8020}
 * @param host where it runs
 * @param agentVersion its version
 * @param appliedPolicyVersion the policy version it applies, or {@code null} if it has none yet
 */
public record AgentReport(String instance, @Nullable String host, @Nullable String agentVersion, @Nullable Long appliedPolicyVersion)
{
}
