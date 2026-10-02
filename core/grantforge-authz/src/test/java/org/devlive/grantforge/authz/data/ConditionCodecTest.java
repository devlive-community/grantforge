// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.persistence.secured.DataField;
import org.devlive.grantforge.persistence.secured.DataFieldType;
import org.devlive.grantforge.persistence.secured.SecuredEntityDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class ConditionCodecTest
{
    private static final SecuredEntityDefinition USERS = new SecuredEntityDefinition("user", "Users", Object.class, List.of(
            new DataField("username", "User name", DataFieldType.TEXT, List.of()),
            new DataField("status", "Status", DataFieldType.CHOICE, List.of("ACTIVE", "DISABLED")),
            new DataField("age", "Age", DataFieldType.NUMBER, List.of()),
            new DataField("admin", "Admin", DataFieldType.BOOLEAN, List.of()),
            new DataField("lastLoginAt", "Last sign-in", DataFieldType.TIME, List.of()),
            new DataField("unitId", "Department", DataFieldType.NUMBER, List.of())), "id", null, false, true, null);

    private static ConditionCodec.Result read(String json)
    {
        return ConditionCodec.read(json, USERS, "condition");
    }

    private static List<FieldIssue> issues(String json)
    {
        return read(json).issues();
    }

    @Test
    void readsNestedConditionsAndWritesThemBackTheSame()
    {
        String json = "{\"and\":[{\"field\":\"status\",\"op\":\"eq\",\"value\":\"ACTIVE\"},"
                + "{\"or\":[{\"field\":\"unitId\",\"op\":\"in\",\"value\":{\"var\":\"subject.orgUnitIds\"}},"
                + "{\"not\":{\"field\":\"username\",\"op\":\"starts_with\",\"value\":\"tmp-\"}}]},"
                + "{\"field\":\"lastLoginAt\",\"op\":\"gte\",\"value\":\"2026-01-01T00:00:00Z\"},"
                + "{\"field\":\"age\",\"op\":\"in\",\"value\":[18,21.5]},"
                + "{\"field\":\"admin\",\"op\":\"eq\",\"value\":false},"
                + "{\"field\":\"username\",\"op\":\"is_null\"}]}";
        ConditionCodec.Result result = read(json);
        assertThat(result.issues()).isEmpty();
        Condition.AllOf all = (Condition.AllOf) result.condition();
        assertThat(all).isNotNull();
        assertThat(all.conditions()).hasSize(6);
        assertThat(all.conditions().get(0)).isEqualTo(new Condition.Comparison("status", ComparisonOperator.EQ, "ACTIVE", null));
        Condition.AnyOf any = (Condition.AnyOf) all.conditions().get(1);
        assertThat(any.conditions().get(0)).isEqualTo(new Condition.Comparison("unitId", ComparisonOperator.IN, null,
                ConditionVariable.SUBJECT_ORG_UNIT_IDS));
        assertThat(any.conditions().get(1)).isInstanceOf(Condition.Negation.class);
        assertThat(all.conditions().get(2)).isEqualTo(new Condition.Comparison("lastLoginAt", ComparisonOperator.GTE,
                Instant.parse("2026-01-01T00:00:00Z"), null));
        assertThat(all.conditions().get(3)).isEqualTo(new Condition.Comparison("age", ComparisonOperator.IN,
                List.of(new BigDecimal("18"), new BigDecimal("21.5")), null));
        assertThat(ConditionCodec.write(all)).isEqualTo(json);
    }

    @Test
    void refusesWhatTheGrammarDoesNotAllow()
    {
        assertThat(issues("not json")).containsExactly(FieldIssue.of("condition", "error.data.condition.malformed"));
        assertThat(issues("[]")).extracting(FieldIssue::messageKey).containsExactly("error.data.condition.malformed");
        assertThat(issues("{\"and\":[],\"or\":[]}")).extracting(FieldIssue::messageKey).containsExactly("error.data.condition.malformed");
        assertThat(issues("{\"and\":{}}")).extracting(FieldIssue::field).containsExactly("condition.and");
        assertThat(issues("{\"or\":[]}")).containsExactly(FieldIssue.of("condition.or", "error.data.condition.empty-group"));
        assertThat(issues("{\"not\":{\"field\":\"age\",\"op\":\"eq\",\"value\":1},\"x\":1}")).extracting(FieldIssue::field)
                .containsExactly("condition");
        assertThat(issues("{\"field\":\"age\",\"op\":\"eq\",\"value\":1,\"extra\":2}")).extracting(FieldIssue::field)
                .containsExactly("condition");
        assertThat(issues("{\"field\":1,\"op\":\"eq\"}")).extracting(FieldIssue::field).containsExactly("condition");
        assertThat(issues("{\"and\":[{\"field\":\"age\",\"op\":\"eq\",\"value\":1},{\"field\":\"passwordHash\",\"op\":\"eq\",\"value\":\"x\"},"
                + "{\"field\":\"age\",\"op\":\"like\",\"value\":1}]}")).extracting(FieldIssue::field, FieldIssue::messageKey).containsExactly(
                tuple("condition.and[1].field", "error.data.condition.field-unknown"),
                tuple("condition.and[2].op", "error.data.condition.operator-unknown"));
    }

    @Test
    void comparesOnlyWhatSuitsTheField()
    {
        assertThat(issues("{\"field\":\"admin\",\"op\":\"gt\",\"value\":true}")).containsExactly(FieldIssue.of("condition.op",
                "error.data.condition.operator-not-for-field", "gt", "Admin"));
        assertThat(issues("{\"field\":\"age\",\"op\":\"is_null\",\"value\":1}")).extracting(FieldIssue::messageKey)
                .containsExactly("error.data.condition.value-unexpected");
        assertThat(issues("{\"field\":\"age\",\"op\":\"eq\"}")).extracting(FieldIssue::messageKey).containsExactly("error.data.condition.value-required");
        assertThat(issues("{\"field\":\"age\",\"op\":\"eq\",\"value\":null}")).extracting(FieldIssue::messageKey)
                .containsExactly("error.data.condition.value-required");
        assertThat(issues("{\"field\":\"age\",\"op\":\"eq\",\"value\":\"ten\"}")).containsExactly(FieldIssue.of("condition.value",
                "error.data.condition.value-invalid", "NUMBER"));
        assertThat(issues("{\"field\":\"admin\",\"op\":\"eq\",\"value\":1}")).extracting(FieldIssue::messageKey)
                .containsExactly("error.data.condition.value-invalid");
        assertThat(issues("{\"field\":\"lastLoginAt\",\"op\":\"lt\",\"value\":\"yesterday\"}")).extracting(FieldIssue::messageKey)
                .containsExactly("error.data.condition.value-invalid");
        assertThat(issues("{\"field\":\"lastLoginAt\",\"op\":\"lt\",\"value\":5}")).extracting(FieldIssue::messageKey)
                .containsExactly("error.data.condition.value-invalid");
        assertThat(issues("{\"field\":\"username\",\"op\":\"eq\",\"value\":\"" + "x".repeat(257) + "\"}")).extracting(FieldIssue::messageKey)
                .containsExactly("error.data.condition.value-invalid");
        assertThat(issues("{\"field\":\"status\",\"op\":\"eq\",\"value\":\"GONE\"}")).containsExactly(FieldIssue.of("condition.value",
                "error.data.condition.choice-unknown", "ACTIVE, DISABLED"));
        assertThat(issues("{\"field\":\"status\",\"op\":\"eq\",\"value\":3}")).extracting(FieldIssue::messageKey)
                .containsExactly("error.data.condition.value-invalid");
        assertThat(issues("{\"field\":\"status\",\"op\":\"in\",\"value\":[\"ACTIVE\",\"GONE\"]}")).extracting(FieldIssue::field)
                .containsExactly("condition.value[1]");
        assertThat(issues("{\"field\":\"status\",\"op\":\"in\",\"value\":\"ACTIVE\"}")).extracting(FieldIssue::messageKey)
                .containsExactly("error.data.condition.value-invalid");
        assertThat(issues("{\"field\":\"status\",\"op\":\"in\",\"value\":[]}")).extracting(FieldIssue::messageKey)
                .containsExactly("error.data.condition.value-invalid");
        String many = "[" + String.join(",", Collections.nCopies(101, "1")) + "]";
        assertThat(issues("{\"field\":\"age\",\"op\":\"in\",\"value\":" + many + "}")).containsExactly(FieldIssue.of("condition.value",
                "error.data.condition.too-many-values", 100));
    }

    @Test
    void variablesMustBeKnownAndFitTheComparison()
    {
        assertThat(read("{\"field\":\"username\",\"op\":\"eq\",\"value\":{\"var\":\"subject.username\"}}").issues()).isEmpty();
        assertThat(read("{\"field\":\"lastLoginAt\",\"op\":\"lt\",\"value\":{\"var\":\"now\"}}").issues()).isEmpty();
        assertThat(issues("{\"field\":\"username\",\"op\":\"eq\",\"value\":{\"var\":\"subject.password\"}}")).containsExactly(
                FieldIssue.of("condition.value", "error.data.condition.variable-unknown", "subject.password"));
        assertThat(issues("{\"field\":\"age\",\"op\":\"eq\",\"value\":{\"var\":\"subject.orgUnitIds\"}}")).containsExactly(
                FieldIssue.of("condition.value", "error.data.condition.variable-mismatch", "subject.orgUnitIds"));
        assertThat(issues("{\"field\":\"username\",\"op\":\"in\",\"value\":{\"var\":\"subject.id\"}}")).extracting(FieldIssue::messageKey)
                .containsExactly("error.data.condition.variable-mismatch");
        assertThat(issues("{\"field\":\"username\",\"op\":\"eq\",\"value\":{\"var\":1}}")).extracting(FieldIssue::messageKey)
                .containsExactly("error.data.condition.malformed");
        assertThat(issues("{\"field\":\"username\",\"op\":\"eq\",\"value\":{\"var\":\"now\",\"x\":1}}")).extracting(FieldIssue::messageKey)
                .containsExactly("error.data.condition.malformed");
    }

    @Test
    void limitsDepthAndSize()
    {
        String leaf = "{\"field\":\"age\",\"op\":\"eq\",\"value\":1}";
        String five = leaf;
        for (int depth = 0; depth < 5; depth++) {
            five = "{\"and\":[" + five + "]}";
        }
        assertThat(read(five).issues()).isEmpty();
        assertThat(issues("{\"and\":[" + five + "]}")).extracting(FieldIssue::messageKey).contains("error.data.condition.too-deep");
        String negations = leaf;
        for (int depth = 0; depth < 6; depth++) {
            negations = "{\"not\":" + negations + "}";
        }
        assertThat(issues(negations)).extracting(FieldIssue::messageKey).contains("error.data.condition.too-deep");
        String wide = "{\"or\":[" + String.join(",", Collections.nCopies(50, leaf)) + "]}";
        assertThat(issues(wide)).containsExactly(FieldIssue.of("condition", "error.data.condition.too-large", 50));
    }

    @Test
    void writesEveryKindOfValue()
    {
        Condition condition = new Condition.AnyOf(List.of(new Condition.Comparison("admin", ComparisonOperator.EQ, true, null),
                new Condition.Comparison("lastLoginAt", ComparisonOperator.LT, Instant.EPOCH, null),
                new Condition.Comparison("username", ComparisonOperator.IN, List.of("a", "b"), null)));
        assertThat(ConditionCodec.write(condition)).isEqualTo("{\"or\":[{\"field\":\"admin\",\"op\":\"eq\",\"value\":true},"
                + "{\"field\":\"lastLoginAt\",\"op\":\"lt\",\"value\":\"1970-01-01T00:00:00Z\"},"
                + "{\"field\":\"username\",\"op\":\"in\",\"value\":[\"a\",\"b\"]}]}");
        assertThatThrownBy(() -> ConditionCodec.write(new Condition.Comparison("username", ComparisonOperator.IN,
                Arrays.asList("a", null), null))).isInstanceOf(NullPointerException.class);
    }
}
