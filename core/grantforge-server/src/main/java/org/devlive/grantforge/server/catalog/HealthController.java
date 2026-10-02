// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.CatalogHealthService;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.server.web.PathIds;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static java.util.Objects.requireNonNull;

/** The catalog check-up: what silently does not work in an application's resources, dependencies and grants. */
@RestController
public final class HealthController
{
    private final CatalogHealthService health;

    /**
     * Creates the controller.
     *
     * @param health the check-up
     */
    public HealthController(CatalogHealthService health)
    {
        this.health = requireNonNull(health, "health");
    }

    /**
     * Checks an application; the grants of every tenant are included.
     *
     * @param id the application
     * @return the findings
     */
    @RequirePermission("platform.catalog.health")
    @GetMapping("/api/v1/applications/{id}/health")
    public HealthReportResponse check(@PathVariable String id)
    {
        return HealthReportResponse.from(health.check(PathIds.parse(id, "application")));
    }
}
