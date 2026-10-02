// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.devlive.grantforge.service.WarehouseProvider;
import org.devlive.grantforge.service.domain.PolicyPriority;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PolicyRulesTest
{
    private static final ServiceTypeDefinition WAREHOUSE = WarehouseProvider.DEFINITION;
    private static final PolicyItemSpec ALICE_SELECTS = PolicyItemSpec.access(List.of("alice"), List.of(), List.of("select"));

    private static PolicyCommand command(PolicyDocument document)
    {
        return new PolicyCommand("sales", null, PolicyPriority.NORMAL, true, List.of(), document);
    }

    private static Map<String, ResourceValues> levels(Object... pairs)
    {
        Map<String, ResourceValues> levels = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            levels.put((String) pairs[index], (ResourceValues) pairs[index + 1]);
        }
        return levels;
    }

    private static List<FieldIssue> check(PolicyType type, PolicyDocument document)
    {
        return PolicyRules.check(WAREHOUSE, type, command(document));
    }

    private static List<FieldIssue> access(Map<String, ResourceValues> resources, PolicyItemSpec... allow)
    {
        return check(PolicyType.ACCESS, PolicyDocument.allowing(resources, allow));
    }

    @Test
    void acceptsSoundPoliciesOfEveryKind()
    {
        assertThat(access(levels("database", ResourceValues.of("sales"), "table", ResourceValues.of("*")), ALICE_SELECTS)).isEmpty();
        assertThat(access(levels("path", new ResourceValues(List.of("/data"), false, true)), ALICE_SELECTS)).isEmpty();
        PolicyItemSpec everyone = new PolicyItemSpec(List.of(), List.of(PolicyItemSpec.PUBLIC), List.of(), List.of("select", "update"),
                List.of(new ConditionValues("ip-range", List.of("10.0.0.0/8"))), null, null, null);
        assertThat(check(PolicyType.ACCESS, new PolicyDocument(levels("database", ResourceValues.of("*")), List.of(everyone),
                List.of(ALICE_SELECTS), List.of(everyone), List.of(ALICE_SELECTS),
                List.of(new ValidityPeriod(Instant.parse("2026-01-01T00:00:00Z"), null))))).isEmpty();

        Map<String, ResourceValues> column = levels("database", ResourceValues.of("hr"), "table", ResourceValues.of("people"),
                "column", new ResourceValues(List.of("id"), true, false));
        PolicyItemSpec masked = new PolicyItemSpec(List.of(), List.of("ops"), List.of(), List.of("select"), List.of(), "custom",
                "left(ssn, 3)", null);
        assertThat(check(PolicyType.DATA_MASK, PolicyDocument.allowing(column, masked))).isEmpty();
        PolicyItemSpec filtered = new PolicyItemSpec(List.of(), List.of(), List.of("analyst"), List.of("select"), List.of(), null, null,
                "region = 'eu'");
        assertThat(check(PolicyType.ROW_FILTER, PolicyDocument.allowing(levels("database", ResourceValues.of("hr"), "table",
                ResourceValues.of("people")), filtered))).isEmpty();
    }

    @Test
    void refusesKindsTheTypeDoesNotSupport()
    {
        ServiceTypeDefinition plain = ServiceTypeDefinition.builder("plain").resources(ResourceDefinition.builder("x").build())
                .accessTypes(AccessTypeDefinition.of("read", "Read")).build();
        assertThat(PolicyRules.check(plain, PolicyType.DATA_MASK, command(PolicyDocument.allowing(Map.of()))))
                .containsExactly(FieldIssue.of("type", "error.policy.type-unsupported", "DATA_MASK"));
        assertThat(PolicyRules.check(plain, PolicyType.ACCESS, command(PolicyDocument.allowing(levels("x", ResourceValues.of("a"))))))
                .isEmpty();
    }

    @Test
    void namesAndLabelsHaveLimits()
    {
        PolicyDocument document = PolicyDocument.allowing(levels("database", ResourceValues.of("*")));
        assertThat(PolicyRules.check(WAREHOUSE, PolicyType.ACCESS, new PolicyCommand(" ", "d".repeat(513), PolicyPriority.OVERRIDE,
                true, Collections.nCopies(1, "x".repeat(65)), document))).extracting(FieldIssue::field, FieldIssue::messageKey)
                .containsExactly(tuple("name", "error.policy.required"), tuple("description", "error.policy.too-long"),
                        tuple("labels", "error.policy.too-long"));
        List<String> labels = IntStream.range(0, 11).mapToObj(Integer::toString).toList();
        assertThat(PolicyRules.check(WAREHOUSE, PolicyType.ACCESS, new PolicyCommand("n".repeat(129), null, PolicyPriority.NORMAL,
                true, labels, document))).extracting(FieldIssue::field).containsExactly("name", "labels");
    }

    @Test
    void resourcesFormOneChainFromTheTopDownToAnEnd()
    {
        assertThat(access(Map.of())).containsExactly(FieldIssue.of("resources", "error.policy.required"));
        assertThat(access(levels("schema", ResourceValues.of("x")))).containsExactly(FieldIssue.of("resources.schema",
                "error.policy.resource-unknown"));
        assertThat(access(levels("table", ResourceValues.of("x")))).containsExactly(FieldIssue.of("resources",
                "error.policy.resources-not-a-chain"));
        assertThat(access(levels("database", ResourceValues.of("x"), "path", ResourceValues.of("/")))).containsExactly(FieldIssue.of(
                "resources", "error.policy.resources-not-a-chain"));
        assertThat(access(levels("database", ResourceValues.of("x"), "column", ResourceValues.of("id")))).containsExactly(
                FieldIssue.of("resources", "error.policy.resources-not-a-chain"));
        assertThat(access(levels("database", ResourceValues.of("x"), "table", ResourceValues.of("t"))).isEmpty()).isTrue();
        ServiceTypeDefinition strict = ServiceTypeDefinition.builder("strict").resources(ResourceDefinition.builder("a").label("A")
                .build(), ResourceDefinition.builder("b").parent("a").build()).accessTypes(AccessTypeDefinition.of("read", "Read")).build();
        assertThat(PolicyRules.check(strict, PolicyType.ACCESS, command(PolicyDocument.allowing(levels("a", ResourceValues.of("x"))))))
                .containsExactly(FieldIssue.of("resources", "error.policy.resources-incomplete", "A"));
    }

    @Test
    void valuesUseOnlyWhatTheirLevelSupports()
    {
        assertThat(access(levels("database", new ResourceValues(List.of(" ", ""), true, true))))
                .extracting(FieldIssue::field, FieldIssue::messageKey).containsExactly(
                        tuple("resources.database", "error.policy.required"),
                        tuple("resources.database", "error.policy.excludes-unsupported"),
                        tuple("resources.database", "error.policy.recursive-unsupported"));
        List<String> many = IntStream.range(0, 101).mapToObj(Integer::toString).toList();
        assertThat(access(levels("database", new ResourceValues(many, false, false)))).containsExactly(FieldIssue.of("resources.database",
                "error.policy.too-many", 100));
        assertThat(access(levels("database", ResourceValues.of("x".repeat(1025))))).containsExactly(FieldIssue.of("resources.database",
                "error.policy.too-long", 1024));
    }

    @Test
    void itemsNameSomeoneAndOnlyKnownAccessTypesAndConditions()
    {
        PolicyItemSpec nobody = new PolicyItemSpec(List.of(), List.of(), List.of(), List.of(), List.of(
                new ConditionValues("weekday", List.of("mon")), new ConditionValues("ip-range", List.of())), null, null, null);
        assertThat(access(levels("database", ResourceValues.of("x")), nobody)).extracting(FieldIssue::field, FieldIssue::messageKey)
                .containsExactly(tuple("allow[0].subjects", "error.policy.no-subject"), tuple("allow[0].accessTypes", "error.policy.required"),
                        tuple("allow[0].conditions.weekday", "error.policy.condition-unknown"),
                        tuple("allow[0].conditions.ip-range", "error.policy.required"));
        Map<String, ResourceValues> column = levels("database", ResourceValues.of("x"), "table", ResourceValues.of("t"), "column",
                ResourceValues.of("c"));
        assertThat(access(column, PolicyItemSpec.access(List.of("alice"), List.of(), List.of("select", "update", "drop"))))
                .containsExactly(FieldIssue.of("allow[0].accessTypes", "error.policy.access-type-unknown", "update, drop"));
        List<String> crowd = IntStream.range(0, 101).mapToObj(index -> "u" + index).toList();
        List<String> addresses = IntStream.range(0, 101).mapToObj(index -> "10.0.0." + index).toList();
        PolicyItemSpec big = new PolicyItemSpec(crowd, crowd, crowd, List.of("select"), List.of(new ConditionValues("ip-range", addresses)),
                null, null, null);
        assertThat(access(levels("database", ResourceValues.of("x")), big)).extracting(FieldIssue::field).containsExactly(
                "allow[0].users", "allow[0].groups", "allow[0].roles", "allow[0].conditions.ip-range");
        assertThat(access(levels("database", ResourceValues.of("x")), Collections.nCopies(101, ALICE_SELECTS).toArray(PolicyItemSpec[]::new)))
                .containsExactly(FieldIssue.of("allow", "error.policy.too-many", 100));
        assertThat(access(levels("schema", ResourceValues.of("x")), PolicyItemSpec.access(List.of("alice"), List.of(), List.of("all"))))
                .extracting(FieldIssue::field).containsExactly("resources.schema");
    }

    @Test
    void eachKindHasItsOwnParts()
    {
        Map<String, ResourceValues> database = levels("database", ResourceValues.of("x"));
        PolicyItemSpec odd = new PolicyItemSpec(List.of("alice"), List.of(), List.of(), List.of("select"), List.of(), "redact", null,
                "1 = 1");
        assertThat(access(database, odd)).extracting(FieldIssue::field, FieldIssue::messageKey).containsExactly(
                tuple("allow[0].maskType", "error.policy.not-for-type"), tuple("allow[0].rowFilter", "error.policy.not-for-type"));

        PolicyDocument masking = new PolicyDocument(database, List.of(ALICE_SELECTS), List.of(ALICE_SELECTS), List.of(ALICE_SELECTS),
                List.of(ALICE_SELECTS), List.of());
        assertThat(check(PolicyType.DATA_MASK, masking)).extracting(FieldIssue::field, FieldIssue::messageKey).containsExactly(
                tuple("resources", "error.policy.resource-not-supported"), tuple("allow[0].maskType", "error.policy.required"),
                tuple("allowExceptions", "error.policy.not-for-type"), tuple("deny", "error.policy.not-for-type"),
                tuple("denyExceptions", "error.policy.not-for-type"));
        PolicyItemSpec unknownMask = new PolicyItemSpec(List.of("alice"), List.of(), List.of(), List.of("select"), List.of(), "shuffle",
                "x".repeat(4001), "1 = 1");
        assertThat(check(PolicyType.DATA_MASK, PolicyDocument.allowing(database, unknownMask))).extracting(FieldIssue::field)
                .containsExactly("resources", "allow[0].maskType", "allow[0].maskValue", "allow[0].rowFilter");

        PolicyItemSpec noFilter = new PolicyItemSpec(List.of("alice"), List.of(), List.of(), List.of("select"), List.of(), "redact", null,
                null);
        PolicyItemSpec longFilter = new PolicyItemSpec(List.of("alice"), List.of(), List.of(), List.of("select"), List.of(), null, null,
                "x".repeat(4001));
        assertThat(check(PolicyType.ROW_FILTER, PolicyDocument.allowing(levels("database", ResourceValues.of("x"), "table",
                ResourceValues.of("t")), noFilter, longFilter))).extracting(FieldIssue::field, FieldIssue::messageKey).containsExactly(
                tuple("allow[0].rowFilter", "error.policy.required"), tuple("allow[0].maskType", "error.policy.not-for-type"),
                tuple("allow[1].rowFilter", "error.policy.too-long"));
    }

    @Test
    void periodsHaveAStartOrAnEndAndEndAfterTheyStart()
    {
        Instant noon = Instant.parse("2026-06-01T12:00:00Z");
        List<ValidityPeriod> periods = List.of(new ValidityPeriod(null, null), new ValidityPeriod(noon, noon),
                new ValidityPeriod(null, noon), new ValidityPeriod(noon, noon.plusSeconds(1)));
        assertThat(check(PolicyType.ACCESS, new PolicyDocument(levels("database", ResourceValues.of("x")), List.of(), List.of(), List.of(),
                List.of(), periods))).containsExactly(FieldIssue.of("validity[0]", "error.policy.validity-empty"),
                FieldIssue.of("validity[1]", "error.policy.validity-order"));
        assertThat(check(PolicyType.ACCESS, new PolicyDocument(levels("database", ResourceValues.of("x")), List.of(), List.of(), List.of(),
                List.of(), Collections.nCopies(11, new ValidityPeriod(noon, null))))).containsExactly(FieldIssue.of("validity",
                "error.policy.too-many", 10));
    }
}
