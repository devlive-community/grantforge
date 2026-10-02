// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ConfigFieldType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
