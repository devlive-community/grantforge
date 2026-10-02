// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

/**
 * Who an agent is, from the token it signed in with.
 *
 * @param tenantId the tenant of the service
 * @param serviceId the service whose policies the agent enforces
 * @param tokenId the token
 */
public record AgentCredential(long tenantId, long serviceId, long tokenId)
{
}
