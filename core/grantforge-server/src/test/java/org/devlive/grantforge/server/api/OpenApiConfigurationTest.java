// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.api;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigurationTest
{
    @Test
    void describesTheApi()
    {
        OpenAPI api = new OpenApiConfiguration().grantForgeOpenApi();

        assertThat(api.getInfo().getTitle()).isEqualTo("GrantForge API");
        assertThat(api.getInfo().getVersion()).isEqualTo(OpenApiConfiguration.API_VERSION);
        assertThat(api.getInfo().getLicense().getName()).isEqualTo("MIT");
        assertThat(api.getServers()).singleElement().satisfies(server -> assertThat(server.getUrl()).isEqualTo("/"));
    }
}
