// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.access;

import org.devlive.grantforge.authz.application.AccessRequestView;
import org.devlive.grantforge.authz.domain.AccessRequestStatus;
import org.devlive.grantforge.server.role.RoleResponse;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An access request.
 *
 * @param id the ID
 * @param requesterId who asks
 * @param requesterName their display name
 * @param requesterUsername their user name
 * @param role the role asked for
 * @param reason why
 * @param requestedDays for how many days
 * @param status where it stands
 * @param requestedAt when it was filed
 * @param decidedByName who decided, if anyone did
 * @param decidedAt when
 * @param comment what the approver said
 * @param validUntil when an approved grant ends
 * @param endedAt when the request or grant ended
 */
public record AccessRequestResponse(String id, String requesterId, String requesterName, @Nullable String requesterUsername, RoleResponse role,
        String reason, int requestedDays, AccessRequestStatus status, Instant requestedAt, @Nullable String decidedByName, @Nullable Instant decidedAt,
        @Nullable String comment, @Nullable Instant validUntil, @Nullable Instant endedAt)
{
    /**
     * Converts a view.
     *
     * @param view the request
     * @return the response
     */
    public static AccessRequestResponse from(AccessRequestView view)
    {
        return new AccessRequestResponse(Long.toString(view.id()), Long.toString(view.requester().id()), view.requester().name(),
                view.requester().detail(), RoleResponse.from(view.role()), view.reason(), view.requestedDays(), view.status(), view.requestedAt(),
                view.decidedBy() == null ? null : view.decidedBy().name(), view.decidedAt(), view.comment(), view.validUntil(), view.endedAt());
    }
}
