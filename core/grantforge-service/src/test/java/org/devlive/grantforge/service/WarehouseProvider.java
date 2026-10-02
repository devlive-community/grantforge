// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service;

import org.devlive.grantforge.plugin.api.ServiceTypeProvider;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConditionDefinition;
import org.devlive.grantforge.plugin.api.model.DataMaskDefinition;
import org.devlive.grantforge.plugin.api.model.MaskTypeDefinition;
import org.devlive.grantforge.plugin.api.model.MatcherType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.RowFilterDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;

import java.util.List;
import java.util.Set;

/**
 * A plugin for the policy tests: a warehouse whose databases hold tables of columns and whose files are paths, with
 * masking of columns, row filtering of tables and a condition on client addresses.
 */
public final class WarehouseProvider
        implements ServiceTypeProvider
{
    /** The service type the tests check policies against. */
    public static final ServiceTypeDefinition DEFINITION = ServiceTypeDefinition.builder("warehouse").label("Warehouse")
            .resources(ResourceDefinition.builder("database").label("Database").validLeaf(true).excludesSupported(false).build(),
                    ResourceDefinition.builder("table").label("Table").parent("database").validLeaf(true).build(),
                    ResourceDefinition.builder("column").label("Column").parent("table").excludesSupported(true)
                            .accessTypes("select").build(),
                    ResourceDefinition.builder("path").label("Path").matcher(MatcherType.PATH).recursiveSupported(true).build())
            .accessTypes(AccessTypeDefinition.of("select", "Select"), AccessTypeDefinition.of("update", "Update"),
                    AccessTypeDefinition.of("all", "All", "select", "update"))
            .dataMask(new DataMaskDefinition(Set.of("column"), List.of(new MaskTypeDefinition("redact", "Redact", null),
                    new MaskTypeDefinition("custom", "Custom", "{expr}"))))
            .rowFilter(new RowFilterDefinition(Set.of("table")))
            .conditions(ConditionDefinition.of("ip-range", "Client addresses", "ip-range"))
            .build();

    @Override
    public ServiceTypeDefinition definition()
    {
        return DEFINITION;
    }
}
