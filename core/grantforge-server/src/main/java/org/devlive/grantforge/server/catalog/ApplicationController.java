// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.Valid;
import org.devlive.grantforge.authz.application.ApplicationService;
import org.devlive.grantforge.authz.application.ResourceService;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
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

/**
 * The applications of the resource catalog and their resource trees. Administrators read them; platform
 * administrators change them.
 */
@RestController
@RequestMapping("/api/v1/applications")
public final class ApplicationController
{
    private final ApplicationService applications;
    private final ResourceService resources;

    /**
     * Creates the controller.
     *
     * @param applications the catalog's applications
     * @param resources the catalog's resources
     */
    public ApplicationController(ApplicationService applications, ResourceService resources)
    {
        this.applications = requireNonNull(applications, "applications");
        this.resources = requireNonNull(resources, "resources");
    }

    /**
     * Lists the applications, built-in ones first.
     *
     * @param user the session's principal
     * @return the applications
     */
    @RequirePermission("platform.catalog.read")
    @GetMapping
    public List<ApplicationResponse> list(@AuthenticationPrincipal SessionUser user)
    {
        return applications.list(user.accountId()).stream().map(ApplicationResponse::from).toList();
    }

    /**
     * Registers an application.
     *
     * @param user the session's principal
     * @param body the application
     * @return the new application
     */
    @RequirePermission("platform.application.create")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody ApplicationRequest body)
    {
        return ApplicationResponse.from(applications.create(user.accountId(), body.code(), body.name(), body.description()));
    }

    /**
     * Changes the name and description of an application.
     *
     * @param user the session's principal
     * @param id the application
     * @param body the new details
     * @return the application
     */
    @RequirePermission("platform.application.update")
    @PutMapping("/{id}")
    public ApplicationResponse update(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody ApplicationUpdateRequest body)
    {
        return ApplicationResponse.from(applications.update(user.accountId(), PathIds.parse(id, "application"), body.name(),
                body.description()));
    }

    /**
     * Deletes an application without resources; built-in applications stay.
     *
     * @param user the session's principal
     * @param id the application
     */
    @RequirePermission("platform.application.delete")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        applications.delete(user.accountId(), PathIds.parse(id, "application"));
    }

    /**
     * Returns an application's resource tree, parents before children and siblings in order.
     *
     * @param user the session's principal
     * @param id the application
     * @return the resources
     */
    @RequirePermission("platform.catalog.read")
    @GetMapping("/{id}/resources")
    public List<ResourceResponse> resources(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return resources.tree(user.accountId(), PathIds.parse(id, "application")).stream().map(ResourceResponse::from)
                .toList();
    }

    /**
     * Adds a resource as the last child of its parent.
     *
     * @param user the session's principal
     * @param id the application
     * @param body the resource
     * @return the new resource
     */
    @RequirePermission("platform.resource.create")
    @PostMapping("/{id}/resources")
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceResponse createResource(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody ResourceRequest body)
    {
        return ResourceResponse.from(resources.create(user.accountId(), PathIds.parse(id, "application"),
                ResourceController.parent(body.parentId()), requireNonNull(body.type(), "type"), body.code(), body.details()));
    }
}
