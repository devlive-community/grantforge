// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementPlanTest
{
    private final SqlAnalyzer analyzer = new SqlAnalyzer(Fixtures.catalog());

    @Test
    void callersCannotRemoveChecksOrReadsFromAnAnalyzedStatement() throws Refusal
    {
        StatementPlan plan = analyzer.analyze("SELECT card, id, card FROM orders", "sales").get(0);
        TableName orders = new TableName("sales", "orders");

        assertThat(plan.action()).isEqualTo("SELECT");
        assertThat(plan.accesses()).containsExactly(new Access("sales", "orders", "card", "select"),
                new Access("sales", "orders", "id", "select"));
        assertThat(plan.used(orders)).containsExactly("card", "id");
        assertThat(plan.reads()).singleElement().extracting(StatementPlan.Read::table).isEqualTo(orders);
        assertThatThrownBy(() -> plan.accesses().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.reads().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.used(orders).clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.used(new TableName("sales", "missing")).add("card"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(plan.accesses()).hasSize(2);
        assertThat(plan.reads()).hasSize(1);
    }

    @Test
    void preservesTheWriteTargetAndTheColumnsThatMayNeedMaskChecks() throws Refusal
    {
        StatementPlan plan = analyzer.analyze("UPDATE orders SET total = total + 1 WHERE id = 7", "sales").get(0);
        TableName orders = new TableName("sales", "orders");

        assertThat(plan.targets()).containsExactly(orders);
        assertThat(plan.filtered()).isEqualTo(orders);
        assertThat(plan.used(orders)).contains("total", "id");
        assertThatThrownBy(() -> plan.targets().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(plan.targets()).isEqualTo(Set.of(orders));
    }

    @Test
    void recordsSessionChangesSeparatelyFromReadsAndWriteTargets() throws Refusal
    {
        StatementPlan database = analyzer.analyze("USE `sales`", null).get(0);
        StatementPlan charset = analyzer.analyze("SET NAMES utf8mb4", "sales").get(0);
        StatementPlan explain = analyzer.analyze("EXPLAIN SELECT id FROM orders", "sales").get(0);
        StatementPlan view = analyzer.analyze("CREATE VIEW summaries AS SELECT total FROM orders", "sales").get(0);

        assertThat(database.database()).isEqualTo("sales");
        assertThat(database.charset()).isNull();
        assertThat(charset.charset()).isEqualTo("utf8mb4");
        assertThat(charset.database()).isNull();
        assertThat(charset.accesses()).isEmpty();
        assertThat(explain.explain()).isTrue();
        assertThat(explain.view()).isFalse();
        assertThat(view.view()).isTrue();
        assertThat(view.explain()).isFalse();
    }
}
