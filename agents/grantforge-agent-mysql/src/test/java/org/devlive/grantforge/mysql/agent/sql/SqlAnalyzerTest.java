// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlAnalyzerTest
{
    private final SqlAnalyzer analyzer = new SqlAnalyzer(Fixtures.catalog());

    private List<String> accesses(String sql) throws Refusal
    {
        return analyzer.analyze(sql, "sales").stream().flatMap(plan -> plan.accesses().stream())
                .map(access -> access.accessType() + " " + access.resource()).toList();
    }

    private StatementPlan plan(String sql) throws Refusal
    {
        List<StatementPlan> plans = analyzer.analyze(sql, "sales");
        assertThat(plans).hasSize(1);
        return plans.get(0);
    }

    private Refusal refusal(String sql)
    {
        try {
            analyzer.analyze(sql, "sales");
        }
        catch (Refusal refusal) {
            return refusal;
        }
        throw new AssertionError("not refused: " + sql);
    }

    @Test
    void selectsTheColumnsAQueryNames() throws Refusal
    {
        assertThat(accesses("SELECT id, o.total FROM orders o WHERE region = 'EU'"))
                .containsExactlyInAnyOrder("select sales.orders.id", "select sales.orders.total", "select sales.orders.region");
        assertThat(accesses("SELECT `ID`, sales.orders.card FROM `sales`.`orders`")).as("columns compare without case")
                .containsExactlyInAnyOrder("select sales.orders.id", "select sales.orders.card");
        assertThat(accesses("SELECT 1 + 1, NOW(), @@version, @@session.auto_increment_increment AS step")).isEmpty();
    }

    @Test
    void starsSelectEveryColumnOfTheirTables() throws Refusal
    {
        assertThat(accesses("SELECT * FROM items")).containsExactlyInAnyOrder("select sales.items.order_id", "select sales.items.sku",
                "select sales.items.price");
        assertThat(accesses("SELECT c.*, o.id FROM orders o JOIN customers c ON c.id = o.customer_id")).containsExactlyInAnyOrder(
                "select sales.customers.id", "select sales.customers.name", "select sales.customers.email", "select sales.customers.region",
                "select sales.orders.id", "select sales.orders.customer_id");
        assertThat(accesses("SELECT COUNT(*) FROM orders")).as("rows without a column need the table").containsExactly("select sales.orders");
        assertThat(accesses("SELECT * FROM hr.unknown")).as("unknown columns need the table").containsExactly("select hr.unknown");
    }

    @Test
    void unqualifiedColumnsCountForEveryTableInSightThatHasThem() throws Refusal
    {
        assertThat(accesses("SELECT name FROM orders JOIN customers ON customers.id = orders.customer_id WHERE region = 'EU'"))
                .contains("select sales.customers.name", "select sales.orders.region", "select sales.customers.region");
        // The subquery's region is items' own scope plus the outer orders; customers of another branch are out of sight.
        assertThat(accesses("SELECT id FROM orders WHERE EXISTS (SELECT 1 FROM items WHERE order_id = id AND region = 'EU')"
                + " UNION SELECT id FROM customers")).contains("select sales.orders.region").doesNotContain("select sales.customers.region");
        assertThat(accesses("SELECT o.id FROM orders o NATURAL JOIN customers")).as("a natural join compares every shared column")
                .contains("select sales.orders.card", "select sales.customers.email");
        assertThat(accesses("SELECT sku FROM orders JOIN items USING (id)")).contains("select sales.orders.id", "select sales.items.sku");
    }

    @Test
    void derivedTablesAndCommonTableExpressionsAreReadInside() throws Refusal
    {
        assertThat(accesses("SELECT d.total FROM (SELECT total, card AS c FROM orders) d")).containsExactlyInAnyOrder(
                "select sales.orders.total", "select sales.orders.card");
        assertThat(accesses("WITH big AS (SELECT id FROM orders WHERE total > 100) SELECT * FROM big"))
                .containsExactlyInAnyOrder("select sales.orders.id", "select sales.orders.total");
        StatementPlan plan = plan("SELECT * FROM (SELECT id FROM orders) d JOIN items ON items.order_id = d.id");
        assertThat(plan.reads()).extracting(read -> read.table().toString()).containsExactlyInAnyOrder("sales.orders", "sales.items");
    }

    @Test
    void refusesAnExpressionThatHidesATableOfTheSameName()
    {
        assertThat(refusal("SELECT * FROM orders WHERE id IN (WITH orders AS (SELECT 1 AS id) SELECT id FROM orders)").getMessage())
                .contains("common table expression");
    }

    @Test
    void writesNeedTheirOwnAccess() throws Refusal
    {
        assertThat(accesses("INSERT INTO items (order_id, sku) VALUES (1, 'a')")).containsExactly("insert sales.items.order_id",
                "insert sales.items.sku");
        assertThat(accesses("INSERT INTO items SELECT order_id, sku, price FROM items")).as("the query's columns are its own")
                .contains("insert sales.items.price", "select sales.items.price");
        assertThat(accesses("INSERT INTO items (sku) VALUES ('a') ON DUPLICATE KEY UPDATE price = VALUES(price) + price")).containsExactly(
                "insert sales.items.sku", "update sales.items.price", "select sales.items.price");
        assertThat(accesses("REPLACE INTO items VALUES (1, 'a', 2)")).contains("insert sales.items.sku", "delete sales.items");
        assertThat(accesses("UPDATE orders SET total = total * 2 WHERE region = 'EU'")).containsExactlyInAnyOrder("update sales.orders.total",
                "select sales.orders.total", "select sales.orders.region");
        assertThat(accesses("UPDATE orders SET total = 0")).as("an unread target needs no select").containsExactly("update sales.orders.total");
        assertThat(accesses("DELETE FROM orders WHERE id = 1")).containsExactly("delete sales.orders", "select sales.orders.id");
        assertThat(accesses("DELETE o FROM orders o JOIN customers c ON c.id = o.customer_id WHERE c.region = 'EU'"))
                .contains("delete sales.orders", "select sales.customers.region").doesNotContain("delete sales.customers");
    }

    @Test
    void knowsWhatAWriteChanges() throws Refusal
    {
        StatementPlan single = plan("UPDATE orders SET total = 0 WHERE id = 1");
        assertThat(single.targets()).containsExactly(new TableName("sales", "orders"));
        assertThat(single.filtered()).isEqualTo(new TableName("sales", "orders"));
        assertThat(single.used(new TableName("sales", "orders"))).containsExactly("id");
        assertThat(single.reads()).isEmpty();
        StatementPlan joined = plan("UPDATE orders o JOIN customers c ON c.id = o.customer_id SET o.region = c.region");
        assertThat(joined.targets()).containsExactly(new TableName("sales", "orders"));
        assertThat(joined.filtered()).isNull();
        assertThat(joined.reads()).extracting(read -> read.table().table()).containsExactly("customers");
    }

    @Test
    void definitionsNeedTheirAccessTypes() throws Refusal
    {
        assertThat(accesses("CREATE TABLE archive AS SELECT id FROM orders")).containsExactly("create sales.archive", "select sales.orders.id");
        assertThat(accesses("CREATE TABLE copy LIKE orders")).containsExactly("create sales.copy", "select sales.orders");
        assertThat(accesses("DROP TABLE items, hr.salaries")).containsExactly("drop sales.items", "drop hr.salaries");
        assertThat(accesses("TRUNCATE TABLE items")).containsExactly("drop sales.items");
        assertThat(accesses("ALTER TABLE items ADD COLUMN size INT")).containsExactly("alter sales.items");
        assertThat(accesses("RENAME TABLE items TO old_items")).containsExactly("alter sales.items", "drop sales.items",
                "create sales.old_items", "insert sales.old_items");
        assertThat(accesses("CREATE INDEX by_sku ON items (sku)")).containsExactly("index sales.items");
        assertThat(accesses("DROP INDEX by_sku ON items")).containsExactly("index sales.items");
        assertThat(accesses("CREATE DATABASE reports")).containsExactly("create reports");
        assertThat(accesses("DROP DATABASE reports")).containsExactly("drop reports");
        assertThat(accesses("LOAD DATA LOCAL INFILE 'a.csv' INTO TABLE items (sku)")).containsExactly("insert sales.items.sku");
        assertThat(accesses("LOCK TABLES items READ")).containsExactly("select sales.items");
        StatementPlan view = plan("CREATE VIEW cheap AS SELECT sku FROM items WHERE price < 10");
        assertThat(view.view()).isTrue();
        assertThat(view.accesses()).extracting(Access::resource).contains("sales.cheap", "sales.items.sku", "sales.items.price");
    }

    @Test
    void passesStatementsThatUseNoData() throws Refusal
    {
        for (String sql : List.of("BEGIN", "COMMIT", "ROLLBACK", "SET autocommit = 0", "SHOW TABLES", "SHOW CREATE TABLE items",
                "SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED", "DESC items", "KILL QUERY 3", "GRANT SELECT ON sales.* TO u")) {
            assertThat(plan(sql).accesses()).as(sql).isEmpty();
        }
        assertThat(plan("USE `Hr`").database()).isEqualTo("Hr");
        assertThat(plan("SET NAMES 'latin1' COLLATE latin1_swedish_ci").charset()).isEqualTo("latin1");
        assertThat(plan("SET character_set_client = gbk").charset()).isEqualTo("gbk");
        assertThat(accesses("SET @top = (SELECT MAX(total) FROM orders)")).as("a SET that reads is checked").containsExactly(
                "select sales.orders.total");
        StatementPlan explain = plan("EXPLAIN SELECT card FROM orders");
        assertThat(explain.explain()).isTrue();
        assertThat(explain.accesses()).extracting(Access::resource).containsExactly("sales.orders.card");
    }

    @Test
    void refusesWhatItCannotSeeThrough()
    {
        assertThat(refusal("SELECT /*!50000 card, */ id FROM orders").getMessage()).contains("executable comments");
        assertThat(refusal("CALL refresh()").code()).isEqualTo(Refusal.NOT_ALLOWED);
        assertThat(refusal("SELECT discount(total) FROM orders").getMessage()).contains("stored function discount");
        assertThat(refusal("SELECT sales.anything(1)").getMessage()).contains("stored functions");
        assertThat(refusal("PREPARE s FROM 'SELECT card FROM orders'").code()).isEqualTo(Refusal.NOT_ALLOWED);
        assertThat(refusal("CREATE TRIGGER t BEFORE INSERT ON items FOR EACH ROW SET NEW.price = 1").code()).isEqualTo(Refusal.NOT_ALLOWED);
        assertThat(refusal("HANDLER items OPEN").code()).isEqualTo(Refusal.CANNOT_PARSE);
        assertThat(refusal("TABLE items").code()).isEqualTo(Refusal.CANNOT_PARSE);
        assertThat(refusal("USE hr; SELECT * FROM salaries").getMessage()).contains("USE");
        assertThat(refusal("SET NAMES gbk; SELECT id FROM orders").getMessage()).contains("SET NAMES");
        assertThatThrownBy(() -> analyzer.analyze("SELECT * FROM items", null)).isInstanceOfSatisfying(Refusal.class, refused -> {
            assertThat(refused.code()).isEqualTo(Refusal.NO_DATABASE);
            assertThat(refused.state()).isEqualTo("3D000");
        });
    }

    @Test
    void foldsNamesWhenTheServerDoes() throws Refusal
    {
        SqlAnalyzer folding = new SqlAnalyzer(Fixtures.catalog(true));
        List<StatementPlan> plans = folding.analyze("SELECT ID FROM Sales.ORDERS", null);
        assertThat(plans.get(0).accesses()).extracting(Access::resource).containsExactly("sales.orders.id");
        assertThat(folding.analyze("USE Sales", null).get(0).database()).isEqualTo("sales");
    }

    @Test
    void analyzesEveryStatementOfABatch() throws Refusal
    {
        List<StatementPlan> plans = analyzer.analyze("SELECT sku FROM items; UPDATE items SET price = 1", "sales");
        assertThat(plans).extracting(StatementPlan::action).containsExactly("SELECT", "UPDATE");
        assertThat(analyzer.analyze("", "sales")).isEmpty();
    }
}
