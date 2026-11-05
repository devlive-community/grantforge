// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import jakarta.validation.Valid;
import org.devlive.grantforge.authz.application.UserResourceGrantService;
import org.devlive.grantforge.authz.domain.UserResourceGrant;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.common.security.RequireStepUp;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Direct grants to an account in the active tenant. */
@RestController
public final class UserResourceGrantController
{
    private final UserResourceGrantService grants;

    /**
     * Creates the controller.
     *
     * @param grants direct grant service
     */
    public UserResourceGrantController(UserResourceGrantService grants)
    {
        this.grants = grants;
    }

    /**
     * Lists direct grants in the selected application.
     *
     * @param user signed-in user
     * @param id account ID
     * @param applicationId application ID
     * @return direct grants
     */
    @RequirePermission("system.role.grant")
    @GetMapping("/api/v1/users/{id}/resource-grants")
    public List<DirectGrant> list(@AuthenticationPrincipal SessionUser user, @PathVariable String id, @RequestParam String applicationId)
    {
        return grants.list(user.accountId(), PathIds.parse(id, "account"), PathIds.parse(applicationId, "application"))
                .stream().map(DirectGrant::from).toList();
    }

    /**
     * Lists resources the account can use through its roles alone.
     *
     * @param user signed-in user
     * @param id account ID
     * @param applicationId application ID
     * @return role-derived resource IDs
     */
    @RequirePermission("system.role.grant")
    @GetMapping("/api/v1/users/{id}/resource-grants/role-resources")
    public List<String> roleResources(@AuthenticationPrincipal SessionUser user, @PathVariable String id, @RequestParam String applicationId)
    {
        return grants.roleResources(user.accountId(), PathIds.parse(id, "account"), PathIds.parse(applicationId, "application"))
                .stream().sorted().map(String::valueOf).toList();
    }

    /**
     * Applies direct resource grants in the selected application.
     *
     * @param user signed-in user
     * @param id account ID
     * @param body requested changes
     * @return direct grants
     */
    @RequireStepUp
    @RequirePermission("system.role.grant")
    @PutMapping("/api/v1/users/{id}/resource-grants")
    public List<DirectGrant> apply(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody GrantChangesRequest body)
    {
        return grants.apply(user.accountId(), PathIds.parse(id, "account"), PathIds.parse(String.valueOf(body.applicationId()), "application"),
                body.changes().stream().map(GrantChangeRequest::change).toList()).stream().map(DirectGrant::from).toList();
    }

    /**
     * A resource explicitly granted to the user.
     *
     * @param resourceId resource ID
     * @param expiresAt optional expiry
     */
    public record DirectGrant(String resourceId, @Nullable Instant expiresAt)
    {
        private static DirectGrant from(UserResourceGrant grant)
        {
            return new DirectGrant(Long.toString(grant.getResourceId()), grant.getExpiresAt());
        }
    }
}
