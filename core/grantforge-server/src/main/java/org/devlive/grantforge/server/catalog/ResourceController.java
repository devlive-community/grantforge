// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.Valid;
import org.devlive.grantforge.authz.application.ResourceService;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static java.util.Objects.requireNonNull;

/** Changes to single resources of the catalog; platform administrators only. */
@RestController
@RequestMapping("/api/v1/resources")
public final class ResourceController
{
    private final ResourceService resources;

    /**
     * Creates the controller.
     *
     * @param resources the catalog's resources
     */
    public ResourceController(ResourceService resources)
    {
        this.resources = requireNonNull(resources, "resources");
    }

    /**
     * Changes the code and settings of a resource.
     *
     * @param user the session's principal
     * @param id the resource
     * @param body the new details
     * @return the resource
     */
    @RequirePermission("platform.resource.update")
    @PutMapping("/{id}")
    public ResourceResponse update(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody ResourceUpdateRequest body)
    {
        return ResourceResponse.from(resources.update(user.accountId(), PathIds.parse(id, "resource"), body.code(),
                body.details()));
    }

    /**
     * Moves a resource, with everything below it, under another parent and to a position among its siblings.
     *
     * @param user the session's principal
     * @param id the resource
     * @param body where to move it
     * @return the resource
     */
    @RequirePermission("platform.resource.move")
    @PostMapping("/{id}/move")
    public ResourceResponse move(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody ResourceMoveRequest body)
    {
        return ResourceResponse.from(resources.move(user.accountId(), PathIds.parse(id, "resource"), parent(body.parentId()),
                body.position()));
    }

    /**
     * Deletes a resource without children; built-in resources stay.
     *
     * @param user the session's principal
     * @param id the resource
     */
    @RequirePermission("platform.resource.delete")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        resources.delete(user.accountId(), PathIds.parse(id, "resource"));
    }

    static @Nullable Long parent(@Nullable String parentId)
    {
        return parentId == null || parentId.isBlank() ? null : PathIds.parse(parentId.trim(), "resource");
    }
}
