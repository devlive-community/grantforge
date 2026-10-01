// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Describes the GrantForge HTTP API for the generated OpenAPI document ({@code /v3/api-docs}).
 *
 * <p>The document is the contract between server and web console: it is committed as
 * {@code core/grantforge-web/src/api/openapi.json}, the console's TypeScript types are generated from it,
 * and a test fails whenever the server's actual API and the committed contract differ.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration
{
    /** Version of the public HTTP API, independent of the product release version. */
    public static final String API_VERSION = "v1";

    /**
     * Returns the API metadata.
     *
     * @return the OpenAPI root object; springdoc adds the paths and schemas
     */
    @Bean
    public OpenAPI grantForgeOpenApi()
    {
        return new OpenAPI()
                .info(new Info()
                        .title("GrantForge API")
                        .description("Fine-grained, plugin-based authorization platform")
                        .version(API_VERSION)
                        .license(new License().name("MIT").url("https://opensource.org/license/mit")))
                // Relative: the console calls the API on its own origin, and the contract must not depend on
                // the host that generated it.
                .servers(List.of(new Server().url("/")));
    }
}
