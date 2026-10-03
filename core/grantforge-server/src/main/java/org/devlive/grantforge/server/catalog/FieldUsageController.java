// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.DataEntityCatalog;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.server.web.PathIds;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;

/** Where secured fields appear: the APIs that return or accept them, as the code declares them. */
@RestController
public final class FieldUsageController
{
    private final DataEntityCatalog entities;

    /**
     * Creates the controller.
     *
     * @param entities the secured entities and fields of the catalog
     */
    public FieldUsageController(DataEntityCatalog entities)
    {
        this.entities = requireNonNull(entities, "entities");
    }

    /**
     * Returns the APIs a secured field appears in.
     *
     * @param id the field's resource
     * @return the APIs, by path and method
     */
    @RequirePermission("platform.catalog.read")
    @GetMapping("/api/v1/resources/{id}/field-usages")
    public List<FieldUsageResponse> of(@PathVariable String id)
    {
        return entities.usages(PathIds.parse(id, "resource")).stream().map(FieldUsageResponse::from).toList();
    }
}
