// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.audit;

import org.devlive.grantforge.audit.application.AuditEventView;
import org.devlive.grantforge.common.page.CursorPage;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A page of audit events, newest first.
 *
 * @param events the events
 * @param next the cursor of the next page; absent on the last page
 */
public record AuditPageResponse(List<AuditEventResponse> events, @Nullable String next)
{
    /** Copies the list. */
    public AuditPageResponse
    {
        events = List.copyOf(events);
    }

    /**
     * Converts a page.
     *
     * @param page the page
     * @return the response
     */
    public static AuditPageResponse from(CursorPage<AuditEventView> page)
    {
        return new AuditPageResponse(page.items().stream().map(AuditEventResponse::from).toList(), page.nextCursor());
    }
}
