// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import jakarta.validation.Valid;
import org.devlive.grantforge.authz.application.RoleService;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;

/** The roles of the signed-in user's tenant. */
@RestController
@RequestMapping("/api/v1/roles")
public final class RoleController
{
    private final RoleService roles;

    /**
     * Creates the controller.
     *
     * @param roles the tenant's roles
     */
    public RoleController(RoleService roles)
    {
        this.roles = requireNonNull(roles, "roles");
    }

    /**
     * Lists the roles whose code or name contains a text, system roles first.
     *
     * @param user the session's principal
     * @param q the text, or {@code null} for every role
     * @return the roles
     */
    @RequirePermission("system.role.read")
    @GetMapping
    public List<RoleResponse> list(@AuthenticationPrincipal SessionUser user, @RequestParam(required = false) @Nullable String q)
    {
        return roles.list(user.accountId(), q).stream().map(RoleResponse::from).toList();
    }

    /**
     * Returns one role.
     *
     * @param user the session's principal
     * @param id the role
     * @return the role
     */
    @RequirePermission("system.role.read")
    @GetMapping("/{id}")
    public RoleResponse find(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return RoleResponse.from(roles.find(user.accountId(), PathIds.parse(id, "role")));
    }

    /**
     * Creates a custom role.
     *
     * @param user the session's principal
     * @param body the role
     * @return the new role
     */
    @RequirePermission("system.role.create")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse create(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody RoleRequest body)
    {
        return RoleResponse.from(roles.create(user.accountId(), body.code(), body.name(), body.description()));
    }

    /**
     * Changes a custom role.
     *
     * @param user the session's principal
     * @param id the role
     * @param body the new details
     * @return the role
     */
    @RequirePermission("system.role.update")
    @PutMapping("/{id}")
    public RoleResponse update(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody RoleRequest body)
    {
        return RoleResponse.from(roles.update(user.accountId(), PathIds.parse(id, "role"), body.code(), body.name(),
                body.description()));
    }

    /**
     * Creates a custom role as a copy of another, system roles included.
     *
     * @param user the session's principal
     * @param id the role to copy
     * @param body the copy's code and name
     * @return the copy
     */
    @RequirePermission("system.role.create")
    @PostMapping("/{id}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse copy(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody RoleCopyRequest body)
    {
        return RoleResponse.from(roles.copy(user.accountId(), PathIds.parse(id, "role"), body.code(), body.name()));
    }

    /**
     * Enables a custom role.
     *
     * @param user the session's principal
     * @param id the role
     * @return the role
     */
    @RequirePermission("system.role.status")
    @PostMapping("/{id}/enable")
    public RoleResponse enable(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return RoleResponse.from(roles.enable(user.accountId(), PathIds.parse(id, "role"), true));
    }

    /**
     * Disables a custom role; it grants nothing until enabled again.
     *
     * @param user the session's principal
     * @param id the role
     * @return the role
     */
    @RequirePermission("system.role.status")
    @PostMapping("/{id}/disable")
    public RoleResponse disable(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return RoleResponse.from(roles.enable(user.accountId(), PathIds.parse(id, "role"), false));
    }

    /**
     * Deletes a custom role.
     *
     * @param user the session's principal
     * @param id the role
     */
    @RequirePermission("system.role.delete")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        roles.delete(user.accountId(), PathIds.parse(id, "role"));
    }
}
