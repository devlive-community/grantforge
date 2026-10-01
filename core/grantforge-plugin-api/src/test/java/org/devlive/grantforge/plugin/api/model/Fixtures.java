// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import java.util.List;
import java.util.Set;

/** Definitions shaped like the real HDFS and Hive types, to prove the model expresses them. */
final class Fixtures
{
    private Fixtures()
    {
    }

    static ServiceTypeDefinition hdfs()
    {
        return ServiceTypeDefinition.builder("hdfs").label("HDFS").description("Hadoop file system")
                .resources(ResourceDefinition.builder("path").label("Path").matcher(MatcherType.PATH)
                        .recursiveSupported(true).lookupSupported(true).build())
                .accessTypes(AccessTypeDefinition.of("read", "Read"), AccessTypeDefinition.of("write", "Write"),
                        AccessTypeDefinition.of("execute", "Execute"))
                .conditions(ConditionDefinition.of("ip-range", "IP range", "ip-range"))
                .configFields(
                        ConfigField.builder("fs.defaultFS").label("NameNode URL").mandatory()
                                .pattern("hdfs://.+").build(),
                        ConfigField.builder("hadoop.security.authentication").options("simple", "kerberos")
                                .defaultValue("simple").build(),
                        ConfigField.builder("keytab").type(ConfigFieldType.SECRET).build(),
                        ConfigField.builder("lookup.timeout").type(ConfigFieldType.INTEGER).defaultValue("30").build(),
                        ConfigField.builder("tls").type(ConfigFieldType.BOOLEAN).defaultValue("false").build())
                .build();
    }

    static ServiceTypeDefinition hive()
    {
        return ServiceTypeDefinition.builder("hive").label("Hive").version(3)
                .resources(
                        ResourceDefinition.builder("database").caseSensitive(false).lookupSupported(true).build(),
                        ResourceDefinition.builder("table").parent("database").caseSensitive(false).validLeaf(true)
                                .build(),
                        ResourceDefinition.builder("column").parent("table").caseSensitive(false).build(),
                        ResourceDefinition.builder("udf").parent("database").build(),
                        ResourceDefinition.builder("url").matcher(MatcherType.PATH).recursiveSupported(true)
                                .accessTypes("read", "write").build())
                .accessTypes(AccessTypeDefinition.of("select", "Select"), AccessTypeDefinition.of("update", "Update"),
                        AccessTypeDefinition.of("read", "Read"), AccessTypeDefinition.of("write", "Write"),
                        AccessTypeDefinition.of("refresh", "Refresh"),
                        AccessTypeDefinition.of("alter", "Alter", "refresh"),
                        AccessTypeDefinition.of("all", "All", "select", "update", "alter"))
                .dataMask(new DataMaskDefinition(Set.of("column"), List.of(
                        new MaskTypeDefinition("mask-show-last-4", "Last 4", "mask_show_last_n({col}, 4)"),
                        new MaskTypeDefinition("nullify", "Nullify", null))))
                .rowFilter(new RowFilterDefinition(Set.of("table")))
                .capabilities("resource-dependencies")
                .build();
    }
}
