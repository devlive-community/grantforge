// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionTest
{
    @Test
    void keepsItsOwnCopiesAndTakesAValueOrAVariable()
    {
        List<Object> values = new ArrayList<>(List.of("a"));
        Condition.Comparison comparison = new Condition.Comparison("name", ComparisonOperator.IN, values, null);
        values.add("b");
        assertThat(comparison.value()).isEqualTo(List.of("a"));
        List<Condition> members = new ArrayList<>(List.of(comparison));
        Condition.AllOf all = new Condition.AllOf(members);
        Condition.AnyOf any = new Condition.AnyOf(members);
        members.clear();
        assertThat(all.conditions()).hasSize(1);
        assertThat(any.conditions()).hasSize(1);
        assertThat(new Condition.Negation(all).condition()).isEqualTo(all);
        assertThatThrownBy(() -> new Condition.Comparison("name", ComparisonOperator.EQ, "x", ConditionVariable.NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
