// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TableNameTest
{
    @Test
    void keepsSameNamedTablesInDifferentDatabasesSeparateInTheCatalog()
    {
        TableName sales = new TableName("sales", "orders");
        TableName archive = new TableName("archive", "orders");
        Map<TableName, String> schemas = Map.of(sales, "current", archive, "historical");

        assertThat(schemas.get(new TableName("sales", "orders"))).isEqualTo("current");
        assertThat(schemas.get(new TableName("archive", "orders"))).isEqualTo("historical");
        assertThat(sales).hasToString("sales.orders");
        assertThat(sales).isNotEqualTo(archive).isNotEqualTo(new TableName("Sales", "orders"));
    }

    @Test
    void onlyFoldsAndUnquotesResourceNamesWhenTheServerDoes() throws Refusal
    {
        String sql = "SELECT `ID` FROM `SaLeS`.`OrDeRs`";
        StatementPlan folded = new SqlAnalyzer(Fixtures.catalog(true)).analyze(sql, null).get(0);
        StatementPlan preserved = new SqlAnalyzer(Fixtures.catalog(false)).analyze(sql, null).get(0);

        assertThat(folded.reads()).singleElement().extracting(StatementPlan.Read::table).isEqualTo(new TableName("sales", "orders"));
        assertThat(folded.accesses()).containsExactly(new Access("sales", "orders", "id", "select"));
        assertThat(preserved.reads()).singleElement().extracting(StatementPlan.Read::table).isEqualTo(new TableName("SaLeS", "OrDeRs"));
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void rejectsAnIncompleteCatalogKey()
    {
        assertThatNullPointerException().isThrownBy(() -> new TableName(null, "orders")).withMessage("database");
        assertThatNullPointerException().isThrownBy(() -> new TableName("sales", null)).withMessage("table");
    }
}
