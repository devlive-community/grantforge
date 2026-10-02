// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.service.agent.AccessEventView;
import org.devlive.grantforge.service.domain.AccessEvent;
import org.devlive.grantforge.service.domain.AccessOutcome;
import org.devlive.grantforge.service.domain.Enforcer;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An access an agent reported. IDs are strings because they exceed JavaScript's safe integers.
 *
 * @param id the row's id
 * @param agentInstance the agent that reported it
 * @param eventId the agent's name for it
 * @param occurredAt when
 * @param user who
 * @param clientIp from where
 * @param resource which resource
 * @param resourceType the lowest level of the resource
 * @param accessType the access type checked
 * @param action the operation of the system
 * @param outcome allowed or denied
 * @param policyId the policy that decided
 * @param policyName that policy's name now, if it still exists
 * @param policyVersion the policy version the agent applied
 * @param enforcer who decided
 * @param request the request, cut to 1000 characters
 */
public record AccessEventResponse(String id, String agentInstance, String eventId, Instant occurredAt, String user, @Nullable String clientIp,
        String resource, @Nullable String resourceType, String accessType, @Nullable String action, AccessOutcome outcome,
        @Nullable String policyId, @Nullable String policyName, @Nullable Long policyVersion, Enforcer enforcer, @Nullable String request)
{
    /**
     * Converts a view.
     *
     * @param view the view
     * @return the response
     */
    public static AccessEventResponse from(AccessEventView view)
    {
        AccessEvent.Fields event = view.event();
        Long policyId = event.policyId();
        return new AccessEventResponse(Long.toString(view.id()), view.agentInstance(), event.eventId(), event.occurredAt(), event.userName(),
                event.clientIp(), event.resourcePath(), event.resourceType(), event.accessType(), event.actionName(), event.outcome(),
                policyId == null ? null : Long.toString(policyId), view.policyName(), event.policyVersion(), event.enforcer(),
                event.requestText());
    }
}
