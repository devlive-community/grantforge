// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.Valid;
import org.devlive.grantforge.authz.application.ImpactAnalysis;
import org.devlive.grantforge.authz.domain.DependencyKind;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.server.web.PathIds;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * What a change of the shared catalog would do to the roles of every tenant, worked out before it is made: a new
 * dependency, another kind or the removal of one, or a resource disabled or enabled.
 */
@RestController
public final class ImpactController
{
    private final ImpactAnalysis impacts;

    /**
     * Creates the controller.
     *
     * @param impacts works out what changes would do
     */
    public ImpactController(ImpactAnalysis impacts)
    {
        this.impacts = requireNonNull(impacts, "impacts");
    }

    /**
     * Works out what a new dependency would do.
     *
     * @param id the resource that would need the other
     * @param body the resource it would need, and how strongly
     * @return the impact
     */
    @RequirePermission("platform.resource.update")
    @PostMapping("/api/v1/resources/{id}/dependencies/impact")
    public ImpactReportResponse newDependency(@PathVariable String id, @Valid @RequestBody DependencyRequest body)
    {
        return ImpactReportResponse.from(impacts.ofNewDependency(PathIds.parse(id, "resource"),
                PathIds.parse(String.valueOf(body.dependsOnId()).trim(), "resource"), requireNonNullElse(body.kind(), DependencyKind.REQUIRED)));
    }

    /**
     * Works out what changing the kind of a dependency, or removing it, would do.
     *
     * @param id the dependency
     * @param kind its new kind; absent to remove it
     * @return the impact
     */
    @RequirePermission("platform.resource.update")
    @GetMapping("/api/v1/resource-dependencies/{id}/impact")
    public ImpactReportResponse dependencyChange(@PathVariable String id, @RequestParam(required = false) @Nullable DependencyKind kind)
    {
        return ImpactReportResponse.from(impacts.ofDependencyChange(PathIds.parse(id, "dependency"), kind));
    }

    /**
     * Works out what enabling or disabling a resource would do.
     *
     * @param id the resource
     * @param enabled whether it would be enabled
     * @return the impact
     */
    @RequirePermission("platform.resource.update")
    @GetMapping("/api/v1/resources/{id}/impact")
    public ImpactReportResponse enabled(@PathVariable String id, @RequestParam boolean enabled)
    {
        return ImpactReportResponse.from(impacts.ofEnabled(PathIds.parse(id, "resource"), enabled));
    }
}
