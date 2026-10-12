// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogTest
{
    @Test
    void unknownColumnsRequireTablePermissionRatherThanSkippingTheAccess() throws Refusal
    {
        Catalog catalog = Fixtures.catalog();
        SqlAnalyzer analyzer = new SqlAnalyzer(catalog);

        assertThat(analyzer.analyze("SELECT * FROM orders", "sales").get(0).accesses()).containsExactly(
                new Access("sales", "orders", "id", "select"), new Access("sales", "orders", "customer_id", "select"),
                new Access("sales", "orders", "card", "select"), new Access("sales", "orders", "region", "select"),
                new Access("sales", "orders", "total", "select"));
        assertThat(analyzer.analyze("SELECT * FROM missing", "sales").get(0).accesses())
                .containsExactly(new Access("sales", "missing", null, "select"));

        catalog.changed();
        assertThat(analyzer.analyze("SELECT total FROM orders", "sales").get(0).accesses())
                .as("stateless catalogs need no invalidation implementation")
                .containsExactly(new Access("sales", "orders", "total", "select"));
    }

    @Test
    void unavailableMetadataDoesNotProduceAnUncheckedPlan()
    {
        IllegalStateException unavailable = new IllegalStateException("metadata connection unavailable");
        Catalog catalog = new Catalog()
        {
            @Override
            public List<String> columns(TableName table)
            {
                throw unavailable;
            }

            @Override
            public boolean routine(String database, String name)
            {
                return false;
            }

            @Override
            public boolean foldsNames()
            {
                return false;
            }
        };

        assertThatThrownBy(() -> new SqlAnalyzer(catalog).analyze("SELECT * FROM orders", "sales")).isSameAs(unavailable);
    }

    @Test
    void distinguishesStoredFunctionsFromBuiltInsBeforeTheyCanRunWithDefinerRights() throws Refusal
    {
        SqlAnalyzer analyzer = new SqlAnalyzer(Fixtures.catalog());

        assertThat(analyzer.analyze("SELECT ABS(-1)", "sales").get(0).accesses()).isEmpty();
        assertThatThrownBy(() -> analyzer.analyze("SELECT discount(100)", "sales"))
                .isInstanceOfSatisfying(Refusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(Refusal.NOT_ALLOWED);
                    assertThat(refusal.getMessage()).contains("stored function", "definer");
                });
    }
}
