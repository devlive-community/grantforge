// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

/**
 * The answer to a heartbeat.
 *
 * @param policyVersion the service's current policy version; an agent applying another should download a snapshot
 * @param refreshSeconds how many seconds to wait before the next heartbeat
 */
public record Heartbeat(long policyVersion, long refreshSeconds)
{
}
