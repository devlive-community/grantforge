// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.devlive.grantforge.service.domain.AccessEvent;
import org.jspecify.annotations.Nullable;

/**
 * An access event as the console lists it.
 *
 * @param id the row's id
 * @param agentInstance the agent that reported it
 * @param event what happened
 * @param policyName the name of the deciding policy now, or {@code null} if none decided or it is gone
 */
public record AccessEventView(long id, String agentInstance, AccessEvent.Fields event, @Nullable String policyName)
{
}
