// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.AccessRequestStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * An access request as the console shows it.
 *
 * @param id the ID
 * @param requester who asks
 * @param role the role asked for
 * @param reason why
 * @param requestedDays for how many days
 * @param status where it stands
 * @param requestedAt when it was filed
 * @param decidedBy who decided, if anyone did
 * @param decidedAt when
 * @param comment what the approver said
 * @param validUntil when an approved grant ends
 * @param endedAt when the request or grant ended
 */
public record AccessRequestView(long id, Subject requester, RoleView role, String reason, int requestedDays, AccessRequestStatus status,
        Instant requestedAt, @Nullable Subject decidedBy, @Nullable Instant decidedAt, @Nullable String comment, @Nullable Instant validUntil,
        @Nullable Instant endedAt)
{
    /** Checks the parts. */
    public AccessRequestView
    {
        requireNonNull(requester, "requester");
        requireNonNull(role, "role");
        requireNonNull(status, "status");
        requireNonNull(requestedAt, "requestedAt");
    }
}
