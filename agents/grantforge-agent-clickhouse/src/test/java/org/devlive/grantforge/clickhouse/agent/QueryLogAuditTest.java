// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.EventFields;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

class QueryLogAuditTest
{
    private static final Snapshot POLICIES = new ClickHouseSnapshots()
            .allow(List.of("alice"), List.of("select", "insert"), "database", "sales", "table", "*", "column", "*")
            .allow(List.of("bob"), List.of("select"), "database", "sales", "table", "orders", "column", "*")
            .deny(List.of("bob"), List.of("select"), "database", "sales", "table", "orders", "column", "card")
            .allow(List.of("@ops"), List.of("system"), "server", "*").snapshot();

    @TempDir
    Path work;

    private static Row entry(String user, String type, String kind, @Nullable Object... more)
    {
        List<@Nullable Object> pairs = new ArrayList<>(List.of("micros", 1_700_000_000_000_000L, "query_id", "q1", "type", type, "user", user,
                "address", "::ffff:10.0.0.7", "query_kind", kind, "databases", List.of(), "tables", List.of(), "columns", List.of(), "exception_code",
                0, "exception", "", "query", "SELECT 1"));
        pairs.addAll(java.util.Arrays.asList(more));
        return Row.of(pairs.toArray());
    }

    private static List<Map<String, @Nullable Object>> events(Row row)
    {
        return QueryLogAudit.events(row, POLICIES, user -> "carol".equals(user) ? Set.of("ops") : Set.of(),
                table -> List.of("id", "region", "card")).stream().map(EventFields::of).toList();
    }

    private static String denial(String needed)
    {
        return "Code: 497. DB::Exception: bob: Not enough privileges. To execute this query, it's necessary to have the grant " + needed
                + ". (ACCESS_DENIED) (version 26.8.34.13 (official build))";
    }

    @Test
    void reportsReadsAndSaysWhoDecided()
    {
        List<Map<String, @Nullable Object>> alice = events(entry("alice", "QueryFinish", "Select", "tables", List.of("sales.orders"), "columns",
                List.of("sales.orders.id", "sales.orders.region"), "query", "SELECT id, region FROM sales.orders"));
        assertThat(alice).singleElement().satisfies(event -> {
            assertThat(event).containsEntry("user", "alice").containsEntry("resource", "sales.orders").containsEntry("resourceType", "table")
                    .containsEntry("accessType", "select").containsEntry("outcome", "ALLOWED").containsEntry("enforcer", "GRANTFORGE")
                    .containsEntry("policyId", "1").containsEntry("clientIp", "10.0.0.7").containsEntry("action", "SELECT")
                    .containsEntry("request", "SELECT id, region FROM sales.orders").containsEntry("occurredAt", "2023-11-14T22:13:20Z");
        });
        List<Map<String, @Nullable Object>> dave = events(entry("dave", "ExceptionWhileProcessing", "Select", "exception_code", 159, "tables",
                List.of("hr.salaries")));
        assertThat(dave).singleElement().satisfies(event -> assertThat(event).containsEntry("enforcer", "NATIVE").containsEntry("policyId", null));
        assertThat(events(entry("alice", "QueryFinish", "Select", "tables", List.of("sales.orders"))))
                .as("a read of no column, such as count()").singleElement().satisfies(event -> assertThat(event).containsEntry("enforcer", "GRANTFORGE"));
        assertThat(events(entry("bob", "QueryFinish", "Select", "tables", List.of("sales.orders"), "columns", List.of("sales.orders.card"))))
                .singleElement().satisfies(event -> assertThat(event).containsEntry("enforcer", "NATIVE"));
        assertThat(events(entry("bob", "QueryFinish", "Select", "tables", List.of("sales.orders"), "columns",
                List.of("sales.orders.id", "sales.orders.card")))).singleElement().satisfies(event -> assertThat(event).containsEntry("enforcer", "NATIVE"));
        assertThat(QueryLogAudit.events(entry("alice", "QueryFinish", "Select", "tables", List.of("sales.orders")), null, user -> Set.of(),
                table -> List.of())).singleElement().satisfies(event -> assertThat(EventFields.of(event)).containsEntry("enforcer", "NATIVE"));
    }

    @Test
    void tellsWritesFromReadsAndNamesWhatEachKindTouches()
    {
        assertThat(events(entry("alice", "QueryFinish", "Insert", "tables", List.of("sales.orders", "sales.src"), "columns", List.of("sales.src.id"))))
                .extracting(event -> event.get("resource") + " " + event.get("accessType")).containsExactly("sales.orders insert", "sales.src select");
        assertThat(kinds("Alter", "Delete", "Update", "Optimize")).containsExactly("sales.t alter", "sales.t alter", "sales.t alter", "sales.t optimize");
        assertThat(touched(entry("alice", "QueryFinish", "Drop", "tables", List.of("sales.t"), "query", "DROP TABLE sales.t"))).containsExactly("sales.t drop");
        assertThat(touched(entry("alice", "QueryFinish", "Drop", "tables", List.of("sales.t"), "query", " truncate table sales.t")))
                .containsExactly("sales.t truncate");
        assertThat(touched(entry("alice", "QueryFinish", "Drop", "databases", List.of("tmp")))).containsExactly("tmp drop_database");
        assertThat(touched(entry("alice", "QueryFinish", "Create", "databases", List.of("sales"), "tables", List.of("sales.t", "sales.u"))))
                .containsExactly("sales create");
        assertThat(touched(entry("alice", "QueryFinish", "Create", "databases", List.of("tmp")))).containsExactly("* create_database");
        assertThat(touched(entry("alice", "QueryFinish", "Create"))).isEmpty();
        assertThat(touched(entry("carol", "QueryFinish", "System"))).containsExactly("* system");
        assertThat(events(entry("carol", "QueryFinish", "System"))).singleElement().satisfies(event -> assertThat(event)
                .containsEntry("enforcer", "GRANTFORGE").containsEntry("resourceType", "server"));
        assertThat(events(entry("carol", "QueryFinish", "KillQuery"))).singleElement().satisfies(event -> assertThat(event)
                .containsEntry("accessType", "kill_query").containsEntry("action", "KILL QUERY"));
        assertThat(touched(entry("alice", "QueryFinish", "Show"))).isEmpty();
        assertThat(touched(entry("alice", "ExceptionBeforeStart", "Select", "exception_code", 62, "tables", List.of("sales.t")))).isEmpty();
    }

    private static List<String> kinds(String... kinds)
    {
        List<String> touched = new ArrayList<>();
        for (String kind : kinds) {
            touched.addAll(touched(entry("alice", "QueryFinish", kind, "tables", List.of("sales.t"))));
        }
        return touched;
    }

    private static List<String> touched(Row row)
    {
        return events(row).stream().map(event -> event.get("resource") + " " + event.get("accessType")).toList();
    }

    @Test
    void readsWhatARefusalNames()
    {
        Row column = entry("bob", "ExceptionBeforeStart", "Select", "exception_code", 497, "exception", denial("SELECT(card) ON sales.orders"));
        assertThat(events(column)).singleElement().satisfies(event -> assertThat(event).containsEntry("resource", "sales.orders.card")
                .containsEntry("resourceType", "column").containsEntry("outcome", "DENIED").containsEntry("enforcer", "GRANTFORGE")
                .containsEntry("policyId", "3"));
        Row table = entry("bob", "ExceptionBeforeStart", "Select", "exception_code", 497, "exception", denial("SELECT ON sales.orders"));
        assertThat(events(table)).as("the policies refuse one of its columns").singleElement().satisfies(event -> assertThat(event)
                .containsEntry("resource", "sales.orders").containsEntry("enforcer", "GRANTFORGE").containsEntry("policyId", "3"));
        Row items = entry("bob", "ExceptionBeforeStart", "Select", "exception_code", 497, "exception", denial("SELECT ON sales.items"));
        assertThat(events(items)).singleElement().satisfies(event -> assertThat(event).containsEntry("enforcer", "GRANTFORGE")
                .containsEntry("policyId", null));
        Row lag = entry("alice", "ExceptionBeforeStart", "Select", "exception_code", 497, "exception", denial("SELECT ON sales.orders"));
        assertThat(events(lag)).as("GrantForge allows it; ClickHouse has not been told yet").singleElement()
                .satisfies(event -> assertThat(event).containsEntry("enforcer", "NATIVE"));
        Row columns = entry("bob", "ExceptionBeforeStart", "Select", "exception_code", 497, "exception", denial("SELECT(id, card) ON sales.orders"));
        assertThat(events(columns)).singleElement().satisfies(event -> assertThat(event).containsEntry("resource", "sales.orders")
                .containsEntry("policyId", "3"));

        assertThat(touched(entry("bob", "ExceptionBeforeStart", "Create", "exception_code", 497, "exception", denial("CREATE TABLE ON sales.*"))))
                .containsExactly("sales create");
        assertThat(touched(entry("bob", "ExceptionBeforeStart", "Create", "exception_code", 497, "exception", denial("CREATE DATABASE ON *.*"))))
                .containsExactly("* create_database");
        assertThat(touched(entry("bob", "ExceptionBeforeStart", "Show", "exception_code", 497, "exception", denial("SHOW USERS ON *.*"))))
                .containsExactly("* show_users");
        assertThat(touched(entry("bob", "ExceptionBeforeStart", "Alter", "exception_code", 497, "exception", denial("ALTER UPDATE ON `my.db`.`t\\`x`"))))
                .containsExactly("my.db.t`x alter");
        assertThat(touched(entry("bob", "ExceptionBeforeStart", "Select", "exception_code", 497, "exception", "one of the following grants",
                "tables", List.of("hr.salaries")))).containsExactly("hr.salaries select");
        assertThat(events(entry("bob", "ExceptionBeforeStart", "Select", "exception_code", 497, "exception", "denied")))
                .singleElement().satisfies(event -> assertThat(event).containsEntry("resource", "*").containsEntry("accessType", "select")
                        .containsEntry("resourceType", null).containsEntry("enforcer", "NATIVE"));
        assertThat(touched(entry("bob", "ExceptionBeforeStart", "Select", "exception_code", 497, "exception", denial("SELECT ON sales")))).as(
                "a target without a table").containsExactly("* select");
    }

    @Test
    void writesAddressesAsTheyAreAndSplitsNames()
    {
        assertThat(QueryLogAudit.address("")).isNull();
        assertThat(QueryLogAudit.address("::ffff:1.2.3.4")).isEqualTo("1.2.3.4");
        assertThat(QueryLogAudit.address("2001:db8::1")).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(QueryLogAudit.address("gateway.example.com")).isEqualTo("gateway.example.com");
        assertThat(QueryLogAudit.address("1.2.3")).isEqualTo("1.2.0.3");
        assertThat(QueryLogAudit.split("a.b")).containsExactly("a", "b");
        assertThat(QueryLogAudit.split("`a.b`.`c\\`d`")).containsExactly("a.b", "c`d");
    }

    @Test
    void readsBatchByBatchFromWhereTheLastReadEnded() throws IOException
    {
        List<Map<String, Object>> asked = new ArrayList<>();
        FakeClickHouse clickhouse = new FakeClickHouse().answer("toUnixTimestamp64Micro(now64(6)) AS micros", Row.of("micros", 5_000_000L))
                .answer("FROM system.query_log", values -> {
                    asked.add(values);
                    long from = ((Number) requireNonNull(values.get("micros"), "micros")).longValue();
                    if (from < 6_000_000L) {
                        return List.of(entry("alice", "QueryFinish", "Select", "micros", 5_500_000L, "query_id", "a", "tables", List.of("sales.t")),
                                entry("alice", "QueryFinish", "Show", "micros", 6_000_000L, "query_id", "b"));
                    }
                    return from == 6_000_000L ? List.of(entry("alice", "QueryFinish", "Select", "micros", 7_000_000L, "query_id", "c", "tables",
                            List.of("sales.t"))) : List.of();
                });
        Path cursor = work.resolve("state/position");
        List<AccessEvent> sent = new ArrayList<>();
        AgentConfig.QueryLog settings = new AgentConfig.QueryLog(null, Duration.ofSeconds(1), Duration.ofSeconds(5), 2);
        QueryLogAudit audit = new QueryLogAudit(clickhouse, settings, cursor, () -> POLICIES, user -> Set.of(), table -> List.of(), sent::add);
        assertThat(audit.poll()).isEqualTo(3);
        assertThat(sent).hasSize(2);
        assertThat(asked.get(0)).containsEntry("micros", 4_000_000L).containsEntry("id", "").containsEntry("delay", 1000L).containsEntry("limit", 2);
        assertThat(Files.readString(cursor)).isEqualTo("7000000\tc");
        assertThat(audit.poll()).isZero();

        asked.clear();
        QueryLogAudit restarted = new QueryLogAudit(clickhouse, settings, cursor, () -> null, user -> Set.of(), table -> List.of(), sent::add);
        assertThat(restarted.poll()).isZero();
        assertThat(asked).singleElement().satisfies(values -> assertThat(values).containsEntry("micros", 7_000_000L).containsEntry("id", "c"));
    }

    @Test
    void readsTheReplicasOfAClusterAndStartsAtThePresentWhenThePositionIsUnreadable() throws IOException
    {
        List<String> queries = new ArrayList<>();
        FakeClickHouse clickhouse = new FakeClickHouse().answer("now64(6)) AS micros", Row.of("micros", 9_000_000L))
                .answer("clusterAllReplicas('main', system.query_log)", values -> {
                    queries.add(String.valueOf(values.get("micros")));
                    return List.of();
                });
        Path cursor = Files.writeString(work.resolve("position"), "garbage", StandardCharsets.UTF_8);
        QueryLogAudit audit = new QueryLogAudit(clickhouse, new AgentConfig.QueryLog("main", Duration.ZERO, Duration.ofSeconds(5), 10), cursor,
                () -> POLICIES, user -> Set.of(), table -> List.of(), event -> { });
        assertThat(audit.poll()).isZero();
        assertThat(queries).containsExactly("9000000");
        assertThat(Files.readString(cursor)).isEqualTo("9000000\t");

        Path blocked = Files.writeString(work.resolve("file"), "x", StandardCharsets.UTF_8);
        QueryLogAudit unwritable = new QueryLogAudit(clickhouse, new AgentConfig.QueryLog("main", Duration.ZERO, Duration.ofSeconds(5), 10),
                blocked.resolve("position"), () -> POLICIES, user -> Set.of(), table -> List.of(), event -> { });
        assertThat(unwritable.poll()).as("the position is kept in memory").isZero();
    }
}
