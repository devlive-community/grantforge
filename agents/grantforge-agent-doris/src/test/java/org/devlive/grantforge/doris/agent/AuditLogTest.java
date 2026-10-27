// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.EventFields;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class AuditLogTest
{
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 20, 0, 0);

    @TempDir
    Path directory;

    private final List<AccessEvent> events = new ArrayList<>();
    private @Nullable Snapshot snapshot;

    /** A cluster whose audit log is on, eight hours ahead of UTC, with no entries. */
    static FakeDoris cluster()
    {
        return new FakeDoris().answer("SHOW GLOBAL VARIABLES", Row.of("Variable_name", "enable_audit_plugin", "Value", "true"),
                        Row.of("Variable_name", "audit_plugin_max_batch_interval_sec", "Value", "1"))
                .answer("NOW(3)", Row.of("now", NOW, "utc", NOW.minusHours(8), "self", "'grantforge'@'%'"));
    }

    private static Row entry(String time, String id, String user, String state, String error, String statement)
    {
        return Row.of("time", time, "query_id", id, "user", user, "client_ip", "10.0.0.9:51234", "catalog", "internal", "db", "", "state", state,
                "error_message", error, "stmt", statement);
    }

    private AuditLog log(FakeDoris doris, @Nullable Duration delay, int batch)
    {
        return new AuditLog(doris, new AgentConfig.AuditLog(delay, Duration.ofSeconds(5), batch), directory.resolve("position"), () -> snapshot,
                user -> Set.of("analysts"), table -> List.of("id", "region", "card"), events::add);
    }

    private static String[] column(String database, String table, String column)
    {
        return new String[] {"catalog", "internal", "database", database, "table", table, "column", column};
    }

    @Test
    void startsAtThePresentAndMovesOnFromWhereItEnded() throws IOException
    {
        List<Row> entries = List.of(entry("2026-10-27 19:59:50.100", "q1", "alice", "EOF", "", "SELECT region FROM sales.orders"),
                entry("2026-10-27 19:59:51.200", "q2", "bob", "OK", "", "INSERT INTO sales.orders VALUES (1)"),
                entry("2026-10-27 19:59:52.300", "q3", "bob", "EOF", "", "SHOW DATABASES"));
        FakeDoris doris = cluster().answer("__internal_schema.audit_log", parameters -> {
            String after = (String) parameters.get(1);
            int limit = (Integer) parameters.get(5);
            return entries.stream().filter(row -> row.string("time").compareTo(after) > 0).limit(limit).toList();
        });
        AuditLog log = log(doris, null, 2);
        assertThat(log.poll()).as("the first read starts at the present, behind it by the batch interval").isZero();
        List<Object> first = doris.parameters.get(doris.parameters.size() - 1);
        assertThat(first).containsExactly("2026-10-27 19:59:54.000", "2026-10-27 19:59:54.000", "", "2026-10-27 19:59:54.000", "grantforge", 2);
        assertThat(Files.readString(directory.resolve("position"))).isEqualTo("2026-10-27 19:59:54.000\t");

        Files.writeString(directory.resolve("position"), "2026-10-27 19:59:00.000\tq0");
        AuditLog restarted = log(doris, Duration.ZERO, 2);
        assertThat(restarted.poll()).as("in batches").isEqualTo(3);
        assertThat(events).extracting(event -> EventFields.of(event).get("user") + " " + EventFields.of(event).get("resource") + " "
                + EventFields.of(event).get("accessType")).containsExactly("alice internal.sales.orders select", "bob internal.sales.orders load");
        assertThat(EventFields.of(events.get(0))).as("eight hours ahead of UTC").containsEntry("occurredAt", "2026-10-27T11:59:50.100Z");
        assertThat(Files.readString(directory.resolve("position"))).isEqualTo("2026-10-27 19:59:52.300\tq3");
    }

    @Test
    void readsNothingWhileDorisWritesNoAuditLog()
    {
        FakeDoris doris = new FakeDoris().answer("SHOW GLOBAL VARIABLES", Row.of("Variable_name", "enable_audit_plugin", "Value", "false"));
        assertThat(log(doris, null, 10).poll()).isZero();
        assertThat(doris.parameters).hasSize(1);
    }

    @Test
    void readsNothingUntilDorisCreatesTheAuditLogTable()
    {
        FakeDoris doris = cluster().fail("__internal_schema.audit_log", "Table [audit_log] does not exist in database [__internal_schema].");
        assertThat(log(doris, null, 10).poll()).isZero();
        FakeDoris broken = cluster().fail("__internal_schema.audit_log", "Permission denied");
        assertThatThrownBy(() -> log(broken, null, 10).poll()).isInstanceOf(DorisFailure.class);
    }

    @Test
    void anUnreadablePositionStartsAtThePresent() throws IOException
    {
        Files.writeString(directory.resolve("position"), "yesterday\tq");
        FakeDoris doris = cluster();
        assertThat(log(doris, null, 10).poll()).isZero();
        assertThat(Files.readString(directory.resolve("position"))).startsWith("2026-10-27 19:59:54.000");
    }

    @Test
    void followsTheClustersBatchIntervalUnlessSet()
    {
        AuditLog log = log(cluster(), null, 10);
        assertThat(log.delay(Map.of("audit_plugin_max_batch_interval_sec", "60"))).isEqualTo(Duration.ofSeconds(65));
        assertThat(log.delay(Map.of("audit_plugin_max_batch_interval_sec", "soon"))).isEqualTo(Duration.ofSeconds(65));
        assertThat(log.delay(Map.of())).isEqualTo(Duration.ofSeconds(65));
        assertThat(log(cluster(), Duration.ofSeconds(3), 10).delay(Map.of())).isEqualTo(Duration.ofSeconds(3));
        assertThat(AuditLog.offset(NOW, NOW.minusHours(5).minusMinutes(31))).isEqualTo(ZoneOffset.ofHoursMinutes(5, 30));
    }

    @Test
    void namesTheTablesAndColumnsAStatementTouched()
    {
        assertThat(AuditLog.accesses("SELECT o.id, region FROM sales.orders o JOIN hive.lake.events e ON o.id = e.id", "internal", ""))
                .extracting(AuditLog.Access::type, AuditLog.Access::resource).containsExactlyInAnyOrder(
                        tuple("select", "internal.sales.orders"), tuple("select",
                                "hive.lake.events"));
        assertThat(AuditLog.accesses("SELECT id, region FROM orders", "internal", "sales")).singleElement().satisfies(access -> {
            assertThat(access.resource()).isEqualTo("internal.sales.orders");
            assertThat(access.columns()).containsExactly("id", "region");
        });
        assertThat(AuditLog.accesses("SELECT * FROM orders", "internal", "")).as("a table without a database cannot be named").isEmpty();
        assertThat(AuditLog.accesses("SELECT * FROM sales.orders", "internal", "")).singleElement()
                .satisfies(access -> assertThat(access.columns()).as("a star reads the whole table").isEmpty());
        assertThat(types("INSERT INTO sales.t2 SELECT id FROM sales.orders")).containsExactlyInAnyOrder("load internal.sales.t2",
                "select internal.sales.orders");
        assertThat(types("DELETE FROM sales.orders WHERE id = 1")).contains("load internal.sales.orders");
        assertThat(types("TRUNCATE TABLE sales.orders")).containsExactly("load internal.sales.orders");
        assertThat(types("DROP TABLE `sales`.`orders`")).containsExactly("drop internal.sales.orders");
        assertThat(types("ALTER TABLE sales.orders ADD COLUMN c INT")).containsExactly("alter internal.sales.orders");
        assertThat(types("SHOW CREATE VIEW sales.eu")).containsExactly("show_view internal.sales.eu");
        assertThat(types("CREATE TABLE sales.t3 (a INT) DISTRIBUTED BY HASH(a) BUCKETS 1")).containsExactly("create internal.sales");
        assertThat(types("CREATE DATABASE hive.d2")).containsExactly("create_database hive");
        assertThat(types("DROP DATABASE d2")).containsExactly("drop_database internal.d2");
        assertThat(types("GRANT SELECT_PRIV ON *.*.* TO x")).isEmpty();
        assertThat(types("SHOW DATABASES")).isEmpty();
    }

    private static List<String> types(String statement)
    {
        return AuditLog.accesses(statement, "internal", "").stream().map(access -> access.type() + " " + access.resource()).toList();
    }

    @Test
    void readsWhatEachReleaseSaysItRefused()
    {
        assertThat(AuditLog.denied("errCode = 2, detailMessage = Permission denied: user ['bob'@'%'] does not have privilege for [priv predicate: OR,"
                + " Admin_priv,Select_priv] command on [internal].[sales].[orders].[card]", "SELECT card FROM sales.orders", "internal", ""))
                .singleElement().satisfies(access -> {
                    assertThat(access.resource()).isEqualTo("internal.sales.orders.card");
                    assertThat(access.level()).isEqualTo("column");
                    assertThat(access.type()).isEqualTo("select");
                });
        assertThat(AuditLog.denied("Permission denied: ... [priv predicate: OR, Admin_priv,Load_priv] command on [internal].[sales]", "x", "internal",
                "")).extracting(AuditLog.Access::resource).containsExactly("internal.sales");
        assertThat(AuditLog.denied("Permission denied: ... [priv predicate: OR, Admin_priv,Create_priv] command on [hive]", "x", "internal", ""))
                .extracting(AuditLog.Access::level).containsExactly("catalog");
        assertThat(AuditLog.denied("Permission denied: ... [priv predicate: OR, Admin_priv,Select_priv] command on [i].[d].[t]", "x", "internal", ""))
                .extracting(AuditLog.Access::level).containsExactly("table");
        assertThat(AuditLog.denied("SELECT command denied to user 'bob'@'10.0.0.9' for table 'sales.orders'", "SELECT card FROM sales.orders",
                "internal", "")).extracting(AuditLog.Access::resource).containsExactly("internal.sales.orders");
        assertThat(AuditLog.denied("ALTER TABLE command denied to user 'bob'@'%' for table 'sales: orders'", "ALTER TABLE sales.orders ADD COLUMN c INT",
                "internal", "")).extracting(AuditLog.Access::type).containsExactly("alter");
        assertThat(AuditLog.denied("Access denied; you need (at least one of) the (DROP) privilege(s) for this operation", "DROP TABLE sales.orders",
                "internal", "")).extracting(AuditLog.Access::type, AuditLog.Access::resource)
                .containsExactly(tuple("drop", "internal.sales.orders"));
        assertThat(AuditLog.denied("Access denied for user 'bob' to database 'hr'", "DROP TABLE hr.salaries", "internal", ""))
                .extracting(AuditLog.Access::type).containsExactly("drop");
        assertThat(AuditLog.denied("Unknown table 'nosuch'", "SELECT * FROM sales.nosuch", "internal", "")).as("no refusal").isEmpty();
    }

    @Test
    void saysWhetherGrantForgeDecidedEachAccess()
    {
        DorisSnapshots policies = new DorisSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "*", "*"))
                .allow(List.of("bob"), List.of("select"), column("sales", "orders", "*"));
        long denial = policies.nextId();
        policies.deny(List.of("bob"), List.of("select"), column("sales", "orders", "card"));
        snapshot = policies.snapshot();
        Row allowed = entry("2026-10-27 19:59:50.100", "q1", "alice", "EOF", "", "SELECT id, region FROM sales.orders");
        assertThat(fields(allowed)).singleElement().satisfies(event -> assertThat(event).containsEntry("enforcer", "GRANTFORGE")
                .containsEntry("clientIp", "10.0.0.9").containsEntry("action", "SELECT").containsEntry("request", "SELECT id, region FROM sales.orders")
                .containsEntry("resourceType", "table"));

        Row refused = entry("2026-10-27 19:59:51.100", "q2", "bob", "ERR", "SELECT command denied to user 'bob'@'%' for table 'sales.orders'",
                "SELECT card FROM sales.orders");
        assertThat(fields(refused)).singleElement().satisfies(event -> assertThat(event).containsEntry("outcome", "DENIED")
                .containsEntry("enforcer", "GRANTFORGE").containsEntry("policyId", Long.toString(denial)));

        Row outside = entry("2026-10-27 19:59:52.100", "q3", "dave", "EOF", "", "SELECT * FROM hr.salaries");
        assertThat(fields(outside)).singleElement().satisfies(event -> assertThat(event).containsEntry("enforcer", "NATIVE"));
        snapshot = null;
        assertThat(fields(outside)).singleElement().satisfies(event -> assertThat(event).containsEntry("enforcer", "NATIVE"));
        Row failed = entry("2026-10-27 19:59:53.100", "q4", "dave", "ERR", "Unknown table 'x'", "SELECT * FROM hr.x");
        assertThat(fields(failed)).isEmpty();
    }

    private List<Map<String, @Nullable Object>> fields(Row row)
    {
        return AuditLog.events(row, ZoneOffset.UTC, snapshot, user -> Set.of(), table -> List.of("id", "region", "card")).stream()
                .map(EventFields::of).toList();
    }

    @Test
    void readsAddressesAndNames()
    {
        assertThat(AuditLog.address("10.0.0.9:51234")).isEqualTo("10.0.0.9");
        assertThat(AuditLog.address("[::1]:51234")).isEqualTo("::1");
        assertThat(AuditLog.address("::1")).isEqualTo("::1");
        assertThat(AuditLog.address("")).isNull();
        assertThat(AuditLog.split("`a.b`.c")).containsExactly("a.b", "c");
        assertThat(AuditLog.split("`a``b`")).containsExactly("a`b");
    }
}
