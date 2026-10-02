// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConditionDefinition;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ConfigFieldType;
import org.devlive.grantforge.plugin.api.model.DataMaskDefinition;
import org.devlive.grantforge.plugin.api.model.MaskTypeDefinition;
import org.devlive.grantforge.plugin.api.model.MatcherType;
import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.RowFilterDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ServiceTypeResponseTest
{
    @Test
    void describesTheSettingsAndResourceLevelsOfAType()
    {
        ServiceTypeDefinition definition = ServiceTypeDefinition.builder("demo").label("Demo")
                .resources(ResourceDefinition.builder("database").build(), ResourceDefinition.builder("table").parent("database").build())
                .accessTypes(AccessTypeDefinition.of("select", "Select"))
                .configFields(ConfigField.builder("mode").label("Mode").type(ConfigFieldType.ENUM).options("fast", "safe")
                        .defaultValue("fast").description("How hard to try").build())
                .build();
        ServiceTypeResponse response = ServiceTypeResponse.from(definition);
        assertThat(response.name()).isEqualTo("demo");
        assertThat(response.configFields()).containsExactly(new ServiceTypeResponse.Field("mode", "Mode", ConfigFieldType.ENUM, false,
                "fast", List.of("fast", "safe"), null, "How hard to try"));
        assertThat(response.resources()).extracting(ServiceTypeResponse.Level::name, ServiceTypeResponse.Level::parent)
                .containsExactly(tuple("database", null), tuple("table", "database"));
    }

    @Test
    void describesWhatPoliciesOfTheTypeCanSay()
    {
        ServiceTypeDefinition definition = ServiceTypeDefinition.builder("warehouse").label("Warehouse")
                .resources(ResourceDefinition.builder("database").validLeaf(true).build(),
                        ResourceDefinition.builder("column").parent("database").accessTypes("update", "select").build(),
                        ResourceDefinition.builder("path").matcher(MatcherType.PATH).recursiveSupported(true).build())
                .accessTypes(AccessTypeDefinition.of("select", "Select"), AccessTypeDefinition.of("update", "Update"),
                        AccessTypeDefinition.of("all", "All", "update", "select"))
                .dataMask(new DataMaskDefinition(Set.of("column"), List.of(new MaskTypeDefinition("hash", "Hash", "hash({col})"))))
                .rowFilter(new RowFilterDefinition(Set.of("database")))
                .conditions(ConditionDefinition.of("ip-range", "Client addresses", "ip-range"))
                .build();
        ServiceTypeResponse response = ServiceTypeResponse.from(definition);
        assertThat(response.policyTypes()).containsExactly(PolicyType.ACCESS, PolicyType.DATA_MASK, PolicyType.ROW_FILTER);
        assertThat(response.accessTypes()).last().isEqualTo(new ServiceTypeResponse.Access("all", "All", List.of("select", "update")));
        assertThat(response.maskTypes()).containsExactly(new ServiceTypeResponse.Mask("hash", "Hash", "hash({col})"));
        assertThat(response.maskableResources()).containsExactly("column");
        assertThat(response.filterableResources()).containsExactly("database");
        assertThat(response.conditions()).containsExactly(new ServiceTypeResponse.Condition("ip-range", "Client addresses"));
        assertThat(response.resources()).extracting(ServiceTypeResponse.Level::validLeaf, ServiceTypeResponse.Level::accessTypes,
                ServiceTypeResponse.Level::recursiveSupported, ServiceTypeResponse.Level::matcher)
                .containsExactly(tuple(true, List.of(), false, MatcherType.WILDCARD), tuple(false, List.of("select", "update"), false,
                        MatcherType.WILDCARD), tuple(false, List.of(), true, MatcherType.PATH));

        ServiceTypeResponse plain = ServiceTypeResponse.from(ServiceTypeDefinition.builder("plain")
                .resources(ResourceDefinition.builder("x").build()).accessTypes(AccessTypeDefinition.of("read", "Read")).build());
        assertThat(plain.policyTypes()).containsExactly(PolicyType.ACCESS);
        assertThat(plain.maskTypes()).isEmpty();
        assertThat(plain.maskableResources()).isEmpty();
        assertThat(plain.filterableResources()).isEmpty();
    }
}
