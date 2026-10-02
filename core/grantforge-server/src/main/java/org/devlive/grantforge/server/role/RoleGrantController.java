// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import jakarta.validation.Valid;
import org.devlive.grantforge.authz.application.GrantChange;
import org.devlive.grantforge.authz.application.RoleGrantService;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.server.catalog.ImpactReportResponse;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;

/** What the roles of the signed-in user's tenant allow and deny. */
@RestController
public final class RoleGrantController
{
    private final RoleGrantService grants;

    /**
     * Creates the controller.
     *
     * @param grants the tenant's role grants
     */
    public RoleGrantController(RoleGrantService grants)
    {
        this.grants = requireNonNull(grants, "grants");
    }

    /**
     * Returns a role's grants on an application and what they mean.
     *
     * @param user the session's principal
     * @param id the role
     * @param applicationId the application
     * @return the matrix
     */
    @RequirePermission("system.role.read")
    @GetMapping("/api/v1/roles/{id}/grants")
    public GrantMatrixResponse matrix(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @RequestParam String applicationId)
    {
        return GrantMatrixResponse.from(grants.matrix(user.accountId(), PathIds.parse(id, "role"),
                PathIds.parse(applicationId, "application")));
    }

    /**
     * Shows what changes to a role's grants would mean, without saving them.
     *
     * @param user the session's principal
     * @param id the role
     * @param body the changes
     * @return the matrix as it would be
     */
    @RequirePermission("system.role.grant")
    @PostMapping("/api/v1/roles/{id}/grants/preview")
    public GrantMatrixResponse preview(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody GrantChangesRequest body)
    {
        return GrantMatrixResponse.from(grants.preview(user.accountId(), PathIds.parse(id, "role"), application(body), changes(body)));
    }

    /**
     * Works out what changes to a role's grants would do: which roles would gain or lose what, and how many accounts
     * hold them.
     *
     * @param user the session's principal
     * @param id the role
     * @param body the changes
     * @return the impact
     */
    @RequirePermission("system.role.grant")
    @PostMapping("/api/v1/roles/{id}/grants/impact")
    public ImpactReportResponse impact(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody GrantChangesRequest body)
    {
        return ImpactReportResponse.from(grants.impact(user.accountId(), PathIds.parse(id, "role"), application(body), changes(body)));
    }

    /**
     * Changes a role's grants.
     *
     * @param user the session's principal
     * @param id the role
     * @param body the changes
     * @return the matrix after the changes
     */
    @RequirePermission("system.role.grant")
    @PutMapping("/api/v1/roles/{id}/grants")
    public GrantMatrixResponse apply(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody GrantChangesRequest body)
    {
        return GrantMatrixResponse.from(grants.apply(user.accountId(), PathIds.parse(id, "role"), application(body), changes(body)));
    }

    private static long application(GrantChangesRequest body)
    {
        return PathIds.parse(String.valueOf(body.applicationId()).trim(), "application");
    }

    private static List<GrantChange> changes(GrantChangesRequest body)
    {
        return body.changes().stream().map(GrantChangeRequest::change).toList();
    }
}
