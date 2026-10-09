// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.ServiceTypeProvider;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ConfigFieldType;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;

import java.util.List;

/**
 * A plugin for the tests: a service type with every kind of setting, which checks a rule of its own, reaches a
 * "system" unless its address says down or slow, and knows three databases; a lookup of {@code !<reason>} fails for that
 * reason and one of {@code slow} takes too long.
 */
public final class DemoProvider
        implements ServiceTypeProvider
{
    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder("demo").label("Demo")
                .resources(ResourceDefinition.builder("database").lookupSupported(true).build(),
                        ResourceDefinition.builder("table").parent("database").build())
                .accessTypes(AccessTypeDefinition.of("select", "Select"))
                .configFields(ConfigField.builder("url").label("URL").type(ConfigFieldType.STRING).mandatory().pattern("demo://.+")
                                .build(),
                        ConfigField.builder("timeout").label("Timeout").type(ConfigFieldType.INTEGER).defaultValue("30").build(),
                        ConfigField.builder("ssl").label("SSL").type(ConfigFieldType.BOOLEAN).build(),
                        ConfigField.builder("mode").label("Mode").type(ConfigFieldType.ENUM).options("fast", "safe").build(),
                        ConfigField.builder("password").label("Password").type(ConfigFieldType.SECRET).mandatory().build())
                .build();
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        boolean secure = config.require("url").startsWith("demo://secure");
        return "true".equals(config.get("ssl")) && !secure
                ? List.of(new ConfigProblem("ssl", ConfigProblem.Reason.INVALID, "needs a demo://secure address")) : List.of();
    }

    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        String url = config.require("url");
        if (url.contains("slow")) {
            try {
                Thread.sleep(5_000);
            }
            catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            }
        }
        if (url.contains("down")) {
            return ConnectionResult.failed("cannot reach " + url);
        }
        return "s3cret".equals(config.get("password")) ? ConnectionResult.succeeded() : ConnectionResult.failed("wrong password");
    }

    @Override
    public List<String> lookup(LookupRequest request)
    {
        if (request.config().require("url").contains("broken")) {
            throw new IllegalStateException("lookup broke");
        }
        String typed = request.userInput();
        if (typed.startsWith("!")) {
            // Fails for the named reason; the message echoes the password, which must never be shown.
            LookupException.Reason reason = LookupException.Reason.valueOf(typed.substring(1));
            throw new LookupException(reason, reason + " with " + request.config().get("password") + "\nat line two");
        }
        if ("slow".equals(typed)) {
            try {
                Thread.sleep(5_000);
            }
            catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            }
        }
        return List.of("hr", "ops", "operations", "sales").stream().filter(name -> name.startsWith(typed)).toList();
    }
}
