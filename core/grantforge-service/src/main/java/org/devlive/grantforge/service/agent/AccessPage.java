// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Some access events, newest first, and where the next ones start.
 *
 * @param events the events
 * @param next the cursor of the next page, or {@code null} if there are no more
 */
public record AccessPage(List<AccessEventView> events, @Nullable String next)
{
    /** Copies the events. */
    public AccessPage
    {
        events = List.copyOf(events);
    }
}
