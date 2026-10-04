// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.open;

import org.devlive.grantforge.authz.data.ApplicationAccess;
import org.devlive.grantforge.authz.data.ComparisonOperator;
import org.devlive.grantforge.authz.data.Condition;
import org.devlive.grantforge.authz.data.DataAccess;
import org.devlive.grantforge.authz.data.DataRule;
import org.devlive.grantforge.authz.data.DataSubject;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenDataAccessResponseTest
{
    @Test
    void describesTheRulesWithIdsAsStringsAndConditionsInTheirGrammar()
    {
        DataSubject subject = new DataSubject(9_007_199_254_740_993L, 3, "ada", List.of(7L), List.of("/7/"), List.of("buyers"), List.of());
        Condition paid = new Condition.Comparison("status", ComparisonOperator.EQ, "PAID", null);
        ApplicationAccess access = new ApplicationAccess(subject, List.of(7L, 8L), Map.of(
                new DataAccess.Key("order", DataAction.UPDATE), new DataAccess.Rules(List.of(new DataRule(DataScope.CUSTOM_ORGS, null, List.of(9L))),
                        List.of()),
                new DataAccess.Key("order", DataAction.READ), new DataAccess.Rules(List.of(DataRule.of(DataScope.SELF)),
                        List.of(new DataRule(DataScope.CONDITION, paid, List.of())))));

        OpenDataAccessResponse response = OpenDataAccessResponse.from(access);

        assertThat(response.subject().accountId()).isEqualTo("9007199254740993");
        assertThat(response.subject().orgUnitsAndBelow()).containsExactly("7", "8");
        assertThat(response.entities()).extracting(OpenDataAccessResponse.EntityRules::action).containsExactly(DataAction.READ, DataAction.UPDATE);
        assertThat(response.entities().get(0).deny().get(0).condition()).hasToString("{\"field\":\"status\",\"op\":\"eq\",\"value\":\"PAID\"}");
        assertThat(response.entities().get(1).allow().get(0).orgUnitIds()).containsExactly("9");
        assertThat(OpenDataAccessResponse.from(access).version()).isEqualTo(response.version());
    }
}
