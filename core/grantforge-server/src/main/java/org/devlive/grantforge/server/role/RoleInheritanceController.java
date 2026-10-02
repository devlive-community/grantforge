// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import jakarta.validation.Valid;
import org.devlive.grantforge.authz.application.RoleInheritanceService;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;

/** Which roles inherit from which: a role is allowed and denied what the roles it inherits from are. */
@RestController
public final class RoleInheritanceController
{
    private final RoleInheritanceService inheritance;

    /**
     * Creates the controller.
     *
     * @param inheritance inheritance between roles
     */
    public RoleInheritanceController(RoleInheritanceService inheritance)
    {
        this.inheritance = requireNonNull(inheritance, "inheritance");
    }

    /**
     * Returns every inheritance link of the tenant.
     *
     * @return the links
     */
    @RequirePermission("system.role.read")
    @GetMapping("/api/v1/role-links")
    public List<RoleLinkResponse> links()
    {
        return inheritance.links().stream().map(RoleLinkResponse::from).toList();
    }

    /**
     * Returns how a role sits in the inheritance graph.
     *
     * @param id the role
     * @return its parents, ancestors and descendants
     */
    @RequirePermission("system.role.read")
    @GetMapping("/api/v1/roles/{id}/inheritance")
    public RoleInheritanceResponse of(@PathVariable String id)
    {
        return RoleInheritanceResponse.from(inheritance.of(PathIds.parse(id, "role")));
    }

    /**
     * Replaces the roles a role inherits from directly.
     *
     * @param user the session's principal
     * @param id the role
     * @param body the new parents
     * @return how the role now sits in the graph
     */
    @RequirePermission("system.role.inherit")
    @PutMapping("/api/v1/roles/{id}/parents")
    public RoleInheritanceResponse setParents(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody RoleParentsRequest body)
    {
        return RoleInheritanceResponse.from(inheritance.setParents(user.accountId(), PathIds.parse(id, "role"), body.ids()));
    }
}
