// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.server.web.PathIds;
import org.devlive.grantforge.service.agent.AccessAudit;
import org.devlive.grantforge.service.agent.AccessQuery;
import org.devlive.grantforge.service.domain.AccessOutcome;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/** The access events agents reported, as the console reads them. */
@RestController
public final class AccessEventController
{
    private final AccessAudit audit;

    /**
     * Creates the controller.
     *
     * @param audit the access events
     */
    public AccessEventController(AccessAudit audit)
    {
        this.audit = requireNonNull(audit, "audit");
    }

    /**
     * Reads the access events of a service, newest first.
     *
     * @param id the service
     * @param user text the user name contains
     * @param resource text the resource contains
     * @param accessType the access type
     * @param outcome allowed or denied
     * @param from the earliest moment, inclusive
     * @param until the latest moment, exclusive
     * @param limit how many at most
     * @param cursor the {@code next} of the page before
     * @return the events
     */
    @RequirePermission("data.audit.read")
    @GetMapping("/api/v1/services/{id}/access-events")
    public AccessPageResponse search(@PathVariable String id, @RequestParam(required = false) @Nullable String user,
            @RequestParam(required = false) @Nullable String resource, @RequestParam(required = false) @Nullable String accessType,
            @RequestParam(required = false) @Nullable AccessOutcome outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable Instant until,
            @RequestParam(defaultValue = "50") int limit, @RequestParam(required = false) @Nullable String cursor)
    {
        return AccessPageResponse.from(audit.search(PathIds.parse(id, "service"), new AccessQuery(user, resource, accessType, outcome, from,
                until), limit, cursor));
    }
}
