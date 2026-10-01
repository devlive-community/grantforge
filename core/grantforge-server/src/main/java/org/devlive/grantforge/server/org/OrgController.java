// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.org;

import jakarta.validation.Valid;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.identity.application.OrgService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;

/** The organization tree of the signed-in user's tenant. */
@RestController
@RequestMapping("/api/v1/org-units")
public final class OrgController
{
    private final OrgService org;

    /**
     * Creates the controller.
     *
     * @param org the organization tree
     */
    public OrgController(OrgService org)
    {
        this.org = requireNonNull(org, "org");
    }

    /**
     * Returns the whole tree, parents before children and siblings in order.
     *
     * @return the departments
     */
    @RequirePermission("system.org.read")
    @GetMapping
    public List<OrgUnitResponse> tree()
    {
        return org.tree().stream().map(OrgUnitResponse::from).toList();
    }

    /**
     * Creates a department as the last child of its parent.
     *
     * @param user the session's principal
     * @param body the department
     * @return the new department
     */
    @RequirePermission("system.org.create")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrgUnitResponse create(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody OrgUnitRequest body)
    {
        return OrgUnitResponse.from(org.create(user.accountId(), parent(body.parentId()), body.code(), body.name()));
    }

    /**
     * Changes the code and name of a department.
     *
     * @param user the session's principal
     * @param id the department
     * @param body the new details
     * @return the department
     */
    @RequirePermission("system.org.update")
    @PutMapping("/{id}")
    public OrgUnitResponse update(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody OrgUnitRequest body)
    {
        return OrgUnitResponse.from(org.update(user.accountId(), PathIds.parse(id, "department"), body.code(),
                body.name()));
    }

    /**
     * Moves a department, with everything below it, under another parent and to a position among its siblings.
     *
     * @param user the session's principal
     * @param id the department
     * @param body where to move it
     * @return the department
     */
    @RequirePermission("system.org.move")
    @PostMapping("/{id}/move")
    public OrgUnitResponse move(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody OrgUnitMoveRequest body)
    {
        return OrgUnitResponse.from(org.move(user.accountId(), PathIds.parse(id, "department"), parent(body.parentId()),
                body.position()));
    }

    /**
     * Deletes a department without sub-departments.
     *
     * @param user the session's principal
     * @param id the department
     */
    @RequirePermission("system.org.delete")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        org.delete(user.accountId(), PathIds.parse(id, "department"));
    }

    private static @Nullable Long parent(@Nullable String parentId)
    {
        return parentId == null || parentId.isBlank() ? null : PathIds.parse(parentId.trim(), "department");
    }
}
