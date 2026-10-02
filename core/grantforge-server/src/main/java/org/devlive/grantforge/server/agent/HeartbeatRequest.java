// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.service.agent.AgentReport;
import org.jspecify.annotations.Nullable;

/**
 * What an agent reports with each heartbeat.
 *
 * @param instance the name it gives itself, unique within its service, such as {@code namenode-1:8020}
 * @param host where it runs
 * @param agentVersion its version
 * @param appliedPolicyVersion the policy version it applies; left out until it has applied a snapshot
 */
public record HeartbeatRequest(
        @NotBlank @Size(max = 128) @Pattern(regexp = "[\\p{Graph}]+") @Nullable String instance,
        @Size(max = 255) @Nullable String host,
        @Size(max = 64) @Nullable String agentVersion,
        @PositiveOrZero @Nullable Long appliedPolicyVersion)
{
    /**
     * Turns the request into a report.
     *
     * @return the report
     */
    public AgentReport report()
    {
        return new AgentReport(String.valueOf(instance).strip(), host, agentVersion, appliedPolicyVersion);
    }
}
