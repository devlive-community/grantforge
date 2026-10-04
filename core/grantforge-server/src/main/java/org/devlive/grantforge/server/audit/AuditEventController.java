// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.audit;

import org.devlive.grantforge.audit.application.AuditQuery;
import org.devlive.grantforge.audit.application.AuditSearch;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.transfer.CsvFiles;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;

import static java.util.Objects.requireNonNull;

/** The audit log: what users and administrators did, as far as the reader's data policies show it. */
@RestController
public final class AuditEventController
{
    private final AuditSearch search;
    private final Clock clock;

    /**
     * Creates the controller.
     *
     * @param search searches and exports the events
     * @param clock names exports after today's date
     */
    public AuditEventController(AuditSearch search, Clock clock)
    {
        this.search = requireNonNull(search, "search");
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Returns a page of audit events, newest first.
     *
     * @param user the session's principal
     * @param action the kind of event
     * @param outcome whether the action took place
     * @param actor text the actor's name contains
     * @param target the target's ID
     * @param from the earliest time, inclusive
     * @param until the latest time, exclusive
     * @param limit how many at most
     * @param cursor where to go on from, as an earlier page's {@code next}
     * @return the page
     */
    @RequirePermission("system.audit.read")
    @GetMapping("/api/v1/audit-events")
    public AuditPageResponse events(@AuthenticationPrincipal SessionUser user, @RequestParam(required = false) @Nullable AuditAction action,
            @RequestParam(required = false) @Nullable AuditOutcome outcome, @RequestParam(required = false) @Nullable String actor,
            @RequestParam(required = false) @Nullable String target, @RequestParam(required = false) @Nullable Instant from,
            @RequestParam(required = false) @Nullable Instant until, @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) @Nullable String cursor)
    {
        return AuditPageResponse.from(search.search(user.accountId(), new AuditQuery(action, outcome, actor, target, from, until), limit,
                cursor));
    }

    /**
     * Downloads the audit events a filter matches as CSV, newest first, at most {@value AuditSearch#MAX_EXPORT}.
     *
     * @param user the session's principal
     * @param action the kind of event
     * @param outcome whether the action took place
     * @param actor text the actor's name contains
     * @param target the target's ID
     * @param from the earliest time, inclusive
     * @param until the latest time, exclusive
     * @return the CSV file
     */
    @RequirePermission("system.audit.export")
    @GetMapping(value = "/api/v1/audit-events/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal SessionUser user, @RequestParam(required = false) @Nullable AuditAction action,
            @RequestParam(required = false) @Nullable AuditOutcome outcome, @RequestParam(required = false) @Nullable String actor,
            @RequestParam(required = false) @Nullable String target, @RequestParam(required = false) @Nullable Instant from,
            @RequestParam(required = false) @Nullable Instant until)
    {
        return CsvFiles.download("audit", search.export(user.accountId(), new AuditQuery(action, outcome, actor, target, from, until)), clock);
    }
}
