// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.access;

import jakarta.validation.Valid;
import org.devlive.grantforge.authz.application.AccessRequestService;
import org.devlive.grantforge.authz.domain.AccessRequestStatus;
import org.devlive.grantforge.common.security.AuthenticatedEndpoint;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.common.security.RequireStepUp;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Access requests (D-74): every signed-in user asks for requestable roles; approvers grant or turn them down and end
 * grants early; administrators choose which roles may be asked for.
 */
@RestController
public final class AccessRequestController
{
    private final AccessRequestService requests;

    /**
     * Creates the controller.
     *
     * @param requests manages the requests
     */
    public AccessRequestController(AccessRequestService requests)
    {
        this.requests = requireNonNull(requests, "requests");
    }

    /**
     * Lists the roles the signed-in user may ask for.
     *
     * @param user the session's principal
     * @return the roles with where the user stands
     */
    @AuthenticatedEndpoint
    @GetMapping("/api/v1/me/requestable-roles")
    public List<RequestOptionResponse> options(@AuthenticationPrincipal SessionUser user)
    {
        return requests.optionsFor(user.accountId()).stream().map(RequestOptionResponse::from).toList();
    }

    /**
     * Lists the signed-in user's requests.
     *
     * @param user the session's principal
     * @return the requests, newest first
     */
    @AuthenticatedEndpoint
    @GetMapping("/api/v1/me/access-requests")
    public List<AccessRequestResponse> mine(@AuthenticationPrincipal SessionUser user)
    {
        return requests.mine(user.accountId()).stream().map(AccessRequestResponse::from).toList();
    }

    /**
     * Asks for a role.
     *
     * @param user the session's principal
     * @param body the request
     * @return the request
     */
    @AuthenticatedEndpoint
    @PostMapping("/api/v1/me/access-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public AccessRequestResponse request(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody AccessRequestBody body)
    {
        return AccessRequestResponse.from(requests.request(user.accountId(), PathIds.parse(String.valueOf(body.roleId()).strip(), "role"),
                body.reason(), requireNonNull(body.days(), "days")));
    }

    /**
     * Withdraws one of the signed-in user's pending requests.
     *
     * @param user the session's principal
     * @param id the request
     * @return the request
     */
    @AuthenticatedEndpoint
    @PostMapping("/api/v1/me/access-requests/{id}/cancel")
    public AccessRequestResponse cancel(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return AccessRequestResponse.from(requests.cancel(user.accountId(), request(id)));
    }

    /**
     * Lists the tenant's requests for approvers.
     *
     * @param status the states to list, all if left out
     * @return the requests, newest first
     */
    @RequirePermission("system.access-request.read")
    @GetMapping("/api/v1/access-requests")
    public List<AccessRequestResponse> list(@RequestParam(required = false) @Nullable List<AccessRequestStatus> status)
    {
        return requests.list(status == null ? List.of() : status).stream().map(AccessRequestResponse::from).toList();
    }

    /**
     * Grants a pending request.
     *
     * @param user the session's principal
     * @param id the request
     * @param body for how long and what the approver says
     * @return the request
     */
    @RequirePermission("system.access-request.approve")
    @RequireStepUp
    @PostMapping("/api/v1/access-requests/{id}/approve")
    public AccessRequestResponse approve(@AuthenticationPrincipal SessionUser user, @PathVariable String id, @Valid @RequestBody DecisionBody body)
    {
        return AccessRequestResponse.from(requests.approve(user.accountId(), request(id), body.days(), body.comment()));
    }

    /**
     * Turns a pending request down.
     *
     * @param user the session's principal
     * @param id the request
     * @param body why
     * @return the request
     */
    @RequirePermission("system.access-request.approve")
    @PostMapping("/api/v1/access-requests/{id}/reject")
    public AccessRequestResponse reject(@AuthenticationPrincipal SessionUser user, @PathVariable String id, @Valid @RequestBody DecisionBody body)
    {
        return AccessRequestResponse.from(requests.reject(user.accountId(), request(id), body.comment()));
    }

    /**
     * Ends an approved grant early and takes the role back.
     *
     * @param user the session's principal
     * @param id the request
     * @return the request
     */
    @RequirePermission("system.access-request.approve")
    @PostMapping("/api/v1/access-requests/{id}/revoke")
    public AccessRequestResponse revoke(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return AccessRequestResponse.from(requests.revoke(user.accountId(), request(id)));
    }

    /**
     * Lists the roles that may be asked for.
     *
     * @return the roles by name
     */
    @RequirePermission("system.access-request.read")
    @GetMapping("/api/v1/requestable-roles")
    public List<RequestableRoleResponse> requestable()
    {
        return requests.requestableRoles().stream().map(RequestableRoleResponse::from).toList();
    }

    /**
     * Replaces the roles that may be asked for.
     *
     * @param user the session's principal
     * @param body each role with its longest period
     * @return the roles that may be asked for now
     */
    @RequirePermission("system.access-request.configure")
    @PutMapping("/api/v1/requestable-roles")
    public List<RequestableRoleResponse> setRequestable(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody RequestableRolesBody body)
    {
        return requests.setRequestable(user.accountId(), body.maxDays()).stream().map(RequestableRoleResponse::from).toList();
    }

    private static long request(String id)
    {
        return PathIds.parse(id, "request");
    }
}
