// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.example;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.ServiceTypeProvider;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConditionDefinition;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ConfigFieldType;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.devlive.grantforge.plugin.api.model.DataMaskDefinition;
import org.devlive.grantforge.plugin.api.model.MaskTypeDefinition;
import org.devlive.grantforge.plugin.api.model.MatcherType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.RowFilterDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A warehouse that exists only in this class: databases of tables of columns, and file paths. It declares every kind of
 * resource level, masking, row filtering, a condition and settings of each kind, so it doubles as a template. A
 * connection succeeds when the password is {@value #PASSWORD}; lookups list made-up databases and tables, and one of
 * anything starting with {@code offline} fails as the warehouse being unreachable.
 */
public final class ExampleProvider
        implements ServiceTypeProvider
{
    /** The password the example warehouse accepts. */
    public static final String PASSWORD = "example";

    private static final Map<String, List<String>> TABLES = Map.of(
            "sales", List.of("orders", "customers"),
            "hr", List.of("people", "salaries"),
            "ops", List.of("hosts"));

    @Override
    public ServiceTypeDefinition definition()
    {
        return ServiceTypeDefinition.builder("example").label("Example warehouse")
                .description("A sample service type that shows what a plugin can declare")
                .resources(ResourceDefinition.builder("database").label("Database").lookupSupported(true).validLeaf(true)
                                .excludesSupported(false).build(),
                        ResourceDefinition.builder("table").label("Table").parent("database").lookupSupported(true).validLeaf(true).build(),
                        ResourceDefinition.builder("column").label("Column").parent("table").accessTypes("select").build(),
                        ResourceDefinition.builder("path").label("Path").matcher(MatcherType.PATH).recursiveSupported(true).build())
                .accessTypes(AccessTypeDefinition.of("select", "Select"), AccessTypeDefinition.of("update", "Update"),
                        AccessTypeDefinition.of("all", "All", "select", "update"))
                .dataMask(new DataMaskDefinition(Set.of("column"), List.of(new MaskTypeDefinition("redact", "Redact", "redact({col})"),
                        new MaskTypeDefinition("custom", "Custom expression", null))))
                .rowFilter(new RowFilterDefinition(Set.of("table")))
                .conditions(ConditionDefinition.of("ip-range", "Client addresses", "ip-range"))
                .configFields(ConfigField.builder("url").label("Address").type(ConfigFieldType.STRING).mandatory().pattern("example://.+")
                                .description("Such as example://warehouse").build(),
                        ConfigField.builder("timeout").label("Timeout (seconds)").type(ConfigFieldType.INTEGER).defaultValue("30").build(),
                        ConfigField.builder("password").label("Password").type(ConfigFieldType.SECRET).mandatory().build())
                .build();
    }

    @Override
    public List<ConfigProblem> validateConfig(ServiceConfig config)
    {
        return config.getLong("timeout", 30) > 600
                ? List.of(new ConfigProblem("timeout", ConfigProblem.Reason.INVALID, "at most 600 seconds")) : List.of();
    }

    @Override
    public ConnectionResult testConnection(ServiceConfig config)
    {
        return PASSWORD.equals(config.get("password")) ? ConnectionResult.succeeded()
                : ConnectionResult.failed("the example warehouse refused the password");
    }

    @Override
    public List<String> lookup(LookupRequest request)
    {
        if (request.userInput().startsWith("offline")) {
            // Shows how a plugin names why a lookup failed, so the console can say so.
            throw new LookupException(LookupException.Reason.UNREACHABLE, "the example warehouse is offline");
        }
        List<String> values = "table".equals(request.resource())
                ? request.context().getOrDefault("database", List.of()).stream().flatMap(database -> TABLES.getOrDefault(database, List.of())
                        .stream()).toList()
                : "database".equals(request.resource()) ? TABLES.keySet().stream().sorted().toList() : List.of();
        return values.stream().filter(value -> value.startsWith(request.userInput())).limit(request.limit()).toList();
    }
}
