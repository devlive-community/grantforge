// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.plugin;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.ServiceTypeProvider;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ConfigFieldType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;

/** A built-in plugin the server's tests find on their classpath. */
public final class DemoServiceTypeProvider
        implements ServiceTypeProvider
{
    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder("demo").label("Demo").description("A service type for tests")
                .resources(ResourceDefinition.builder("database").build(), ResourceDefinition.builder("table").parent("database").build())
                .accessTypes(AccessTypeDefinition.of("select", "Select"), AccessTypeDefinition.of("update", "Update"))
                .configFields(ConfigField.builder("url").label("URL").type(ConfigFieldType.STRING).mandatory().build(),
                        ConfigField.builder("password").label("Password").type(ConfigFieldType.SECRET).build())
                .build();
    }

    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        return "s3cret".equals(config.get("password")) ? ConnectionResult.succeeded() : ConnectionResult.failed("wrong password");
    }
}
