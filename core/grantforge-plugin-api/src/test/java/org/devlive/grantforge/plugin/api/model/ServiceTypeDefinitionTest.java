// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceTypeDefinitionTest
{
    private static final ServiceTypeDefinition HIVE = Fixtures.hive();
    private static final ServiceTypeDefinition HDFS = Fixtures.hdfs();

    private static ServiceTypeDefinition.Builder minimal()
    {
        return ServiceTypeDefinition.builder("demo").resources(ResourceDefinition.builder("item").build())
                .accessTypes(AccessTypeDefinition.of("use", "Use"));
    }

    private static List<String> problemsOf(ServiceTypeDefinition.Builder builder)
    {
        try {
            builder.build();
        }
        catch (DefinitionException invalid) {
            return invalid.getProblems();
        }
        throw new AssertionError("expected an invalid definition");
    }

    @Test
    void builderDefaultsAndCopies()
    {
        ServiceTypeDefinition demo = minimal().build();

        assertThat(demo.label()).isEqualTo("demo");
        assertThat(demo.version()).isEqualTo(1);
        assertThat(demo.description()).isNull();
        assertThat(demo.policyTypes()).containsExactly(PolicyType.ACCESS);
        assertThat(HDFS.description()).isEqualTo("Hadoop file system");
        assertThat(HIVE.version()).isEqualTo(3);
        assertThat(HIVE.policyTypes()).containsExactly(PolicyType.ACCESS, PolicyType.DATA_MASK, PolicyType.ROW_FILTER);
        assertThat(HIVE.capabilities()).containsExactly("resource-dependencies");
        assertThatThrownBy(() -> HIVE.resources().add(HIVE.resources().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resourceTreesAreWalkedRootFirst()
    {
        assertThat(HIVE.hierarchies()).containsExactly(List.of("database", "table", "column"), List.of("database", "udf"),
                List.of("url"));
        assertThat(HDFS.hierarchies()).containsExactly(List.of("path"));
        assertThat(HIVE.children(null)).extracting(ResourceDefinition::name).containsExactly("database", "url");
        assertThat(HIVE.children("database")).extracting(ResourceDefinition::name).containsExactly("table", "udf");
        assertThat(HIVE.resource("column")).map(ResourceDefinition::parent).contains("table");
        assertThat(HIVE.resource("nothing")).isEmpty();
    }

    @Test
    void policiesNameAChainFromARootToAValidEnd()
    {
        assertThat(HIVE.isValidPolicyResource(List.of("database", "table", "column"))).isTrue();
        assertThat(HIVE.isValidPolicyResource(List.of("database", "table"))).as("table is a valid leaf").isTrue();
        assertThat(HIVE.isValidPolicyResource(List.of("url"))).isTrue();

        assertThat(HIVE.isValidPolicyResource(List.of("database"))).as("database has children").isFalse();
        assertThat(HIVE.isValidPolicyResource(List.of("table", "column"))).as("not from a root").isFalse();
        assertThat(HIVE.isValidPolicyResource(List.of("database", "column"))).as("skips a level").isFalse();
        assertThat(HIVE.isValidPolicyResource(List.of("database", "nothing"))).isFalse();
        assertThat(HIVE.isValidPolicyResource(List.of())).isFalse();
    }

    @Test
    void grantsFollowImplicationsTransitively()
    {
        assertThat(HIVE.impliedAccessTypes("all")).containsExactlyInAnyOrder("all", "select", "update", "alter",
                "refresh");
        assertThat(HIVE.impliedAccessTypes("alter")).containsExactlyInAnyOrder("alter", "refresh");
        assertThat(HIVE.impliedAccessTypes("select")).containsExactly("select");
        assertThat(HIVE.impliedAccessTypes("nothing")).isEmpty();
        assertThat(HIVE.accessType("all")).map(AccessTypeDefinition::label).contains("All");
    }

    @Test
    void implicationCyclesEndTheWalk()
    {
        ServiceTypeDefinition cyclic = ServiceTypeDefinition.builder("cyclic")
                .resources(ResourceDefinition.builder("item").build())
                .accessTypes(AccessTypeDefinition.of("a", "A", "b"), AccessTypeDefinition.of("b", "B", "a")).build();

        assertThat(cyclic.impliedAccessTypes("a")).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void levelsMayRestrictTheirAccessTypes()
    {
        assertThat(HIVE.accessTypesFor("url")).containsExactly("read", "write");
        assertThat(HIVE.accessTypesFor("table")).containsExactly("select", "update", "read", "write", "refresh", "alter",
                "all");
        assertThat(HIVE.accessTypesFor("nothing")).isEmpty();
    }

    @Test
    void configurationIsCheckedAgainstTheFields()
    {
        Map<String, String> values = new HashMap<>();
        values.put("fs.defaultFS", "http://wrong");
        values.put("hadoop.security.authentication", "ldap");
        values.put("lookup.timeout", "soon");
        values.put("tls", "yes");
        values.put("keytab", "c2VjcmV0");
        values.put("zeta", "1");
        values.put("alpha", "2");

        assertThat(HDFS.checkConfig(values)).containsExactly(
                ConfigProblem.of("fs.defaultFS", ConfigProblem.Reason.PATTERN_MISMATCH),
                ConfigProblem.of("hadoop.security.authentication", ConfigProblem.Reason.NOT_AN_OPTION),
                ConfigProblem.of("lookup.timeout", ConfigProblem.Reason.NOT_AN_INTEGER),
                ConfigProblem.of("tls", ConfigProblem.Reason.NOT_A_BOOLEAN),
                ConfigProblem.of("alpha", ConfigProblem.Reason.UNKNOWN_FIELD),
                ConfigProblem.of("zeta", ConfigProblem.Reason.UNKNOWN_FIELD));
        assertThat(HDFS.checkConfig(Map.of("fs.defaultFS", " ")))
                .containsExactly(ConfigProblem.of("fs.defaultFS", ConfigProblem.Reason.REQUIRED));
        assertThat(HDFS.checkConfig(Map.of("fs.defaultFS", "hdfs://nn:8020", "lookup.timeout", "-5"))).isEmpty();
    }

    @Test
    void mandatoryFieldsWithADefaultNeedNoValue()
    {
        ServiceTypeDefinition withDefault = minimal()
                .configFields(ConfigField.builder("port").type(ConfigFieldType.INTEGER).mandatory().defaultValue("80").build())
                .build();

        assertThat(withDefault.checkConfig(Map.of())).isEmpty();
    }

    @Test
    void defaultsFillMissingValues()
    {
        assertThat(HDFS.withDefaults(Map.of("fs.defaultFS", "hdfs://nn", "tls", " ", "extra", "x")))
                .containsExactly(Map.entry("fs.defaultFS", "hdfs://nn"), Map.entry("hadoop.security.authentication", "simple"),
                        Map.entry("lookup.timeout", "30"), Map.entry("tls", "false"));
    }

    @Test
    void everyInconsistencyIsReportedAtOnce()
    {
        ServiceTypeDefinition.Builder broken = ServiceTypeDefinition.builder("broken").version(0)
                .resources(ResourceDefinition.builder("a").parent("b").build(),
                        ResourceDefinition.builder("b").parent("a").build(),
                        ResourceDefinition.builder("c").parent("missing").accessTypes("fly").build(),
                        ResourceDefinition.builder("c").build())
                .accessTypes(AccessTypeDefinition.of("read", "Read", "write"), AccessTypeDefinition.of("read", "Again"))
                .conditions(ConditionDefinition.of("ip", "IP", "ip-range"), ConditionDefinition.of("ip", "IP", "ip-range"))
                .configFields(ConfigField.builder("host").build(), ConfigField.builder("host").build())
                .dataMask(new DataMaskDefinition(Set.of("cell"), List.of(new MaskTypeDefinition("hash", "Hash", null))))
                .rowFilter(new RowFilterDefinition(Set.of("sheet")));

        assertThat(problemsOf(broken)).containsExactlyInAnyOrder(
                "version must be at least 1",
                "resource c declared twice",
                "resource a is part of a parent cycle",
                "resource b is part of a parent cycle",
                "resource c has an unknown parent missing",
                "access type read declared twice",
                "access type implied by read write is not declared",
                "access type of resource c fly is not declared",
                "condition ip declared twice",
                "config field host declared twice",
                "data mask resource cell is not declared",
                "row filter resource sheet is not declared");
        assertThatThrownBy(broken::build).hasMessageStartingWith("invalid service type broken: version must be");
    }

    @Test
    void resourcesAndAccessTypesAreRequired()
    {
        assertThat(problemsOf(ServiceTypeDefinition.builder("empty"))).containsExactly(
                "at least one resource is required", "at least one access type is required");
    }

    @Test
    void malformedValuesAreRefusedImmediately()
    {
        assertThatThrownBy(() -> ServiceTypeDefinition.builder("Bad Name").build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("service type name");
        assertThatThrownBy(() -> minimal().label(" ").build()).hasMessageContaining("label of service type demo");
        assertThatThrownBy(() -> minimal().capabilities("Upper").build()).hasMessageContaining("capability");
        assertThat(minimal().description("  ").build().description()).isNull();
    }
}
