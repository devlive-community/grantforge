// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.service.agent.Heartbeat;

/**
 * The answer to a heartbeat.
 *
 * @param policyVersion the service's current policy version; download a snapshot when it differs from the applied one
 * @param refreshSeconds seconds until the next heartbeat
 */
public record HeartbeatResponse(long policyVersion, long refreshSeconds)
{
    /**
     * Converts a heartbeat.
     *
     * @param heartbeat the heartbeat
     * @return the response
     */
    public static HeartbeatResponse from(Heartbeat heartbeat)
    {
        return new HeartbeatResponse(heartbeat.policyVersion(), heartbeat.refreshSeconds());
    }
}
