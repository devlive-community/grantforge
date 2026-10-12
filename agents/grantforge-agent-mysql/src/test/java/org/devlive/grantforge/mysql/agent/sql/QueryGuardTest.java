// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import org.devlive.grantforge.agent.EventFields;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryGuardTest
{
    private static final List<String> ALICE = List.of("alice");

    private final SqlAnalyzer analyzer = new SqlAnalyzer(Fixtures.catalog());
    private final QueryGuard guard = new QueryGuard(Fixtures.catalog());

    private Verdict check(String sql, Fixtures.Policy policy) throws Refusal
    {
        return check(sql, policy.policies(), false);
    }

    private Verdict check(String sql, Policies policies, boolean prepared) throws Refusal
    {
        return guard.check(sql, analyzer.analyze(sql, "sales"), "alice", "10.1.2.3", policies, prepared);
    }

    private static Fixtures.Policy shop()
    {
        return new Fixtures.Policy().allow("sales", "*", "*", ALICE, "all").allow("sales", null, null, ALICE, "create", "drop");
    }

    @Test
    void allowsWhatPoliciesAllowAndReportsEachAccess() throws Refusal
    {
        Verdict verdict = check("SELECT id, total FROM orders", shop());
        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.sql()).as("nothing to rewrite").isNull();
        assertThat(verdict.events()).extracting(event -> EventFields.of(event).get("resource")).containsExactly("sales.orders.id",
                "sales.orders.total");
        assertThat(verdict.events()).allSatisfy(event -> assertThat(EventFields.of(event)).containsEntry("outcome", "ALLOWED")
                .containsEntry("user", "alice").containsEntry("clientIp", "10.1.2.3").containsEntry("resourceType", "column")
                .containsEntry("accessType", "select").containsEntry("action", "SELECT").containsEntry("policyId", "1")
                .containsEntry("enforcer", "GRANTFORGE").containsEntry("request", "SELECT id, total FROM orders"));
    }

    @Test
    void refusesWithMySqlsOwnErrors() throws Refusal
    {
        Fixtures.Policy noCards = shop().deny("sales", "orders", "card", ALICE, "select");
        Verdict column = check("SELECT id, card FROM orders", noCards);
        assertThat(column.refusal()).isNotNull();
        assertThat(column.refusal().code()).isEqualTo(Refusal.COLUMN_DENIED);
        assertThat(column.refusal().getMessage()).isEqualTo("SELECT command denied to user 'alice' for column 'card' in table 'orders' by GrantForge");
        assertThat(column.events()).extracting(event -> EventFields.of(event).get("outcome")).containsExactly("ALLOWED", "DENIED");
        assertThat(EventFields.of(column.events().get(1))).containsEntry("enforcer", "GRANTFORGE").containsEntry("policyId", "3");

        Verdict table = check("SELECT COUNT(*) FROM hr.salaries", shop());
        assertThat(table.refusal()).isNotNull();
        assertThat(table.refusal().code()).isEqualTo(Refusal.TABLE_DENIED);
        Verdict database = check("CREATE DATABASE reports", shop());
        assertThat(database.refusal()).isNotNull();
        assertThat(database.refusal().code()).isEqualTo(Refusal.DATABASE_DENIED);
        assertThat(check("DROP DATABASE sales", shop()).allowed()).isTrue();
    }

    @Test
    void refusesEverythingWithoutASnapshotAndNothingForAServiceNotInUse() throws Refusal
    {
        Verdict none = check("SELECT id FROM orders", Policies.of(null), false);
        assertThat(none.allowed()).isFalse();
        assertThat(none.events()).singleElement().satisfies(event -> assertThat(EventFields.of(event)).containsEntry("enforcer", "GRANTFORGE")
                .containsEntry("outcome", "DENIED"));
        Verdict off = check("SELECT card FROM hr.salaries", new Fixtures.Policy().disabled().policies(), false);
        assertThat(off.allowed()).isTrue();
        assertThat(off.events()).isEmpty();
    }

    @Test
    void masksColumnsInADerivedTableOfTheSameName() throws Refusal
    {
        Fixtures.Policy masked = shop().mask("sales", "orders", "card", "alice", "mask_show_last_4", null);
        Verdict verdict = check("SELECT o.id, o.card FROM orders o WHERE o.total > 10", masked);
        assertThat(verdict.allowed()).isTrue();
        assertThat(verdict.sql()).isEqualTo("""
                SELECT o.id, o.card
                FROM (
                \tSELECT `id`, `customer_id`
                \t\t, CONCAT(REPEAT('x', GREATEST(CHAR_LENGTH(`card`) - 4, 0)), RIGHT(`card`, 4)) AS `card`
                \t\t, `region`, `total`
                \tFROM orders
                ) o
                WHERE o.total > 10""");
        Verdict unaliased = check("SELECT sales.orders.card FROM sales.orders JOIN items ON items.order_id = orders.id", masked);
        assertThat(unaliased.sql()).contains(") `orders`", "SELECT `orders`.card").contains("JOIN items");
    }

    @Test
    void masksWhereverTheTableIsRead() throws Refusal
    {
        Fixtures.Policy masked = shop().mask("sales", "customers", "email", "alice", "mask_hash", null);
        String sql = check("SELECT id FROM orders WHERE customer_id IN (SELECT id FROM customers WHERE email LIKE '%@example.com')", masked).sql();
        assertThat(sql).contains("SHA2(`email`, 256) AS `email`").contains(") `customers`");
        String insert = check("INSERT INTO customers (id, name, email, region) SELECT id, name, email, region FROM customers", masked).sql();
        assertThat(insert).as("the copy is masked").contains("SHA2(`email`, 256) AS `email`");
        assertThat(check("SELECT id FROM customers", shop().mask("sales", "customers", "email", "alice", "mask_none", null)).sql())
                .as("no mask, no rewrite").isNull();
    }

    @Test
    void filtersRowsOfReadsAndWrites() throws Refusal
    {
        Fixtures.Policy filtered = shop().filter("sales", "orders", "alice", "region = 'EU'")
                .mask("sales", "orders", "card", "alice", "custom", "CONCAT('****', RIGHT({col}, 4))");
        String read = check("SELECT COUNT(*) FROM orders", filtered).sql();
        assertThat(read).contains("CONCAT('****', RIGHT(`card`, 4)) AS `card`").contains("WHERE region = 'EU'");
        String update = check("UPDATE orders SET total = 0 WHERE id = 3", filtered).sql();
        assertThat(update).isEqualTo("UPDATE orders\nSET total = 0\nWHERE region = 'EU'\n\tAND id = 3");
        assertThat(check("DELETE FROM orders", filtered).sql()).isEqualTo("DELETE FROM orders\nWHERE region = 'EU'");
    }

    @Test
    void writesMayNotUseMaskedColumnsOrSpreadFilters() throws Refusal
    {
        Fixtures.Policy masked = shop().mask("sales", "orders", "card", "alice", "mask_null", null);
        Verdict copy = check("UPDATE orders SET region = card", masked);
        assertThat(copy.refusal()).isNotNull();
        assertThat(copy.refusal().getMessage()).contains("masked column sales.orders.card");
        assertThat(check("UPDATE orders SET card = NULL", masked).allowed()).as("writing a masked column reads nothing").isTrue();
        Verdict probe = check("DELETE FROM orders WHERE card LIKE '4%'", masked);
        assertThat(probe.allowed()).isFalse();
        Fixtures.Policy filtered = shop().filter("sales", "orders", "alice", "region = 'EU'");
        Verdict joined = check("UPDATE orders o JOIN customers c ON c.id = o.customer_id SET o.total = 0", filtered);
        assertThat(joined.refusal()).isNotNull();
        assertThat(joined.refusal().getMessage()).contains("single-table");
    }

    @Test
    void viewsMayNotReadWhatMasksChange() throws Refusal
    {
        Fixtures.Policy masked = shop().mask("sales", "orders", "card", "alice", "mask_hash", null);
        Verdict view = check("CREATE VIEW cards AS SELECT card FROM orders", masked);
        assertThat(view.refusal()).isNotNull();
        assertThat(view.refusal().getMessage()).contains("view over sales.orders");
        assertThat(check("CREATE VIEW totals AS SELECT total FROM items JOIN orders ON orders.id = items.order_id", shop()).allowed()).isTrue();
        assertThat(check("CREATE TABLE cards AS SELECT card FROM orders", masked).sql()).as("a copy holds the masked values")
                .contains("SHA2(`card`, 256)");
    }

    @Test
    void neitherClientNorPolicyCommentsSurviveARewrite() throws Refusal
    {
        Fixtures.Policy masked = shop().mask("sales", "orders", "card", "alice", "custom", "SHA2({col}, 256) # trailing");
        String sql = check("SELECT card # the card\n, id -- and id\nFROM orders /* all */", masked).sql();
        assertThat(sql).doesNotContain("#", "--", "/*").contains("SHA2(`card`, 256) AS `card`");
    }

    @Test
    void refusesPolicyExpressionsThatAreNotOneExpression() throws Refusal
    {
        Verdict two = check("SELECT card FROM orders", shop().mask("sales", "orders", "card", "alice", "custom", "1); DROP TABLE orders; SELECT (1"));
        assertThat(two.allowed()).isFalse();
        Verdict broken = check("SELECT id FROM orders", shop().filter("sales", "orders", "alice", "region = "));
        assertThat(broken.allowed()).isFalse();
        Verdict parameter = check("SELECT id FROM orders WHERE id = ?", shop().filter("sales", "orders", "alice", "region = '?'").policies(), true);
        assertThat(parameter.refusal()).isNotNull();
        assertThat(parameter.refusal().getMessage()).contains("prepared statement");
        assertThat(check("SELECT id FROM orders WHERE id = ?", shop().filter("sales", "orders", "alice", "region = 'EU'").policies(), true).sql())
                .contains("WHERE id = ?");
    }

    @Test
    void explainIsCheckedButNotRewritten() throws Refusal
    {
        Fixtures.Policy masked = shop().mask("sales", "orders", "card", "alice", "mask_hash", null);
        Verdict explain = check("EXPLAIN SELECT card FROM orders", masked);
        assertThat(explain.allowed()).isTrue();
        assertThat(explain.sql()).isNull();
        assertThat(check("EXPLAIN SELECT amount FROM hr.salaries", shop()).allowed()).isFalse();
    }
}
