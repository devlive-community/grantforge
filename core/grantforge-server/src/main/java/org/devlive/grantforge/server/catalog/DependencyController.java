// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.Valid;
import org.devlive.grantforge.authz.application.DependencyService;
import org.devlive.grantforge.authz.domain.DependencyKind;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/** What menus, pages, tabs and buttons need to work, such as the APIs a button calls. */
@RestController
public final class DependencyController
{
    private final DependencyService dependencies;

    /**
     * Creates the controller.
     *
     * @param dependencies the catalog's dependencies
     */
    public DependencyController(DependencyService dependencies)
    {
        this.dependencies = requireNonNull(dependencies, "dependencies");
    }

    /**
     * Returns what a resource needs and what needs it.
     *
     * @param user the session's principal
     * @param id the resource
     * @return the dependencies
     */
    @RequirePermission("platform.catalog.read")
    @GetMapping("/api/v1/resources/{id}/dependencies")
    public ResourceDependenciesResponse of(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return ResourceDependenciesResponse.from(dependencies.of(user.accountId(), PathIds.parse(id, "resource")));
    }

    /**
     * Returns every dependency of an application, for drawing them.
     *
     * @param user the session's principal
     * @param id the application
     * @return the dependencies
     */
    @RequirePermission("platform.catalog.read")
    @GetMapping("/api/v1/applications/{id}/dependencies")
    public List<DependencyResponse> graph(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return dependencies.graph(user.accountId(), PathIds.parse(id, "application")).stream().map(DependencyResponse::from)
                .toList();
    }

    /**
     * Makes a resource depend on another of the same application.
     *
     * @param user the session's principal
     * @param id the resource that needs the other
     * @param body the resource it needs
     * @return the dependency
     */
    @RequirePermission("platform.resource.update")
    @PostMapping("/api/v1/resources/{id}/dependencies")
    @ResponseStatus(HttpStatus.CREATED)
    public DependencyResponse add(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody DependencyRequest body)
    {
        return DependencyResponse.from(dependencies.add(user.accountId(), PathIds.parse(id, "resource"),
                PathIds.parse(String.valueOf(body.dependsOnId()).trim(), "resource"),
                requireNonNullElse(body.kind(), DependencyKind.REQUIRED)));
    }

    /**
     * Makes a dependency required or optional.
     *
     * @param user the session's principal
     * @param id the dependency
     * @param body the new kind
     * @return the dependency
     */
    @RequirePermission("platform.resource.update")
    @PutMapping("/api/v1/resource-dependencies/{id}")
    public DependencyResponse changeKind(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody DependencyKindRequest body)
    {
        return DependencyResponse.from(dependencies.changeKind(user.accountId(), PathIds.parse(id, "dependency"),
                requireNonNull(body.kind(), "kind")));
    }

    /**
     * Removes a dependency an administrator added.
     *
     * @param user the session's principal
     * @param id the dependency
     */
    @RequirePermission("platform.resource.update")
    @DeleteMapping("/api/v1/resource-dependencies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        dependencies.remove(user.accountId(), PathIds.parse(id, "dependency"));
    }
}
