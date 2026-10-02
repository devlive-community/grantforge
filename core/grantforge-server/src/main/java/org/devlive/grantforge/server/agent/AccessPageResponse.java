// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.service.agent.AccessPage;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Access events, newest first.
 *
 * @param events the events
 * @param next the cursor for the next page, or {@code null} if there are no more
 */
public record AccessPageResponse(List<AccessEventResponse> events, @Nullable String next)
{
    /** Copies the events. */
    public AccessPageResponse
    {
        events = List.copyOf(events);
    }

    /**
     * Converts a page.
     *
     * @param page the page
     * @return the response
     */
    public static AccessPageResponse from(AccessPage page)
    {
        return new AccessPageResponse(page.events().stream().map(AccessEventResponse::from).toList(), page.next());
    }
}
