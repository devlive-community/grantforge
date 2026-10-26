// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

class ServerLogAuditTest
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Inventory SALES = requireNonNull(ServerStateTest.read(ServerStateTest.server()).databases().get("sales"), "sales").inventory();

    @TempDir
    Path directory;

    private static String[] column(String database, String schema, String table, String column)
    {
        return new String[] {"database", database, "schema", schema, "table", table, "column", column};
    }

    /** A line of the server log as PostgreSQL writes it. */
    static ObjectNode entry(String user, String severity, @Nullable String state, String ps, String message, @Nullable String statement)
    {
        ObjectNode entry = JSON.createObjectNode().put("timestamp", "2026-10-26 02:17:31.835 UTC").put("user", user).put("dbname", "sales")
                .put("remote_host", "10.0.0.5").put("error_severity", severity).put("ps", ps).put("message", message)
                .put("backend_type", "client backend");
        if (state != null) {
            entry.put("state_code", state);
        }
        if (statement != null) {
            entry.put("statement", statement);
        }
        return entry;
    }

    static ObjectNode audit(String user, String fields)
    {
        return entry(user, "LOG", null, "SELECT", "AUDIT: SESSION,1,1," + fields + ",<not logged>", null);
    }

    private static List<AccessEvent> events(JsonNode entry, @Nullable Snapshot snapshot)
    {
        return ServerLogAudit.events(entry, "grantforge", snapshot, user -> Set.of(), database -> SALES);
    }

    private static Map<String, @Nullable Object> only(JsonNode entry, @Nullable Snapshot snapshot)
    {
        List<AccessEvent> events = events(entry, snapshot);
        assertThat(events).hasSize(1);
        return EventFields.of(events.get(0));
    }

    @Test
    void reportsWhatPgauditLoggedAsAllowed()
    {
        Snapshot snapshot = new PostgreSqlSnapshots().allow(List.of("alice"), List.of("select"), column("sales", "crm", "orders", "*")).snapshot();
        Map<String, @Nullable Object> read = only(audit("alice", "READ,SELECT,TABLE,crm.orders,SELECT region FROM crm.orders"), snapshot);
        assertThat(read).containsEntry("user", "alice").containsEntry("resource", "sales.crm.orders").containsEntry("accessType", "select")
                .containsEntry("outcome", "ALLOWED").containsEntry("enforcer", "GRANTFORGE").containsEntry("resourceType", "table")
                .containsEntry("action", "SELECT").containsEntry("request", "SELECT region FROM crm.orders").containsEntry("clientIp", "10.0.0.5");
        assertThat(read.get("occurredAt")).isEqualTo("2026-10-26T02:17:31.835Z");
        Map<String, @Nullable Object> outside = only(audit("bob", "READ,SELECT,TABLE,crm.orders,SELECT 1 FROM crm.orders"), snapshot);
        assertThat(outside).containsEntry("enforcer", "NATIVE");
        assertThat(only(audit("bob", "READ,SELECT,TABLE,crm.orders,x"), null)).containsEntry("enforcer", "NATIVE").containsEntry("policyId", null);
    }

    @Test
    void readsTheAccessTypeOfWrites()
    {
        assertThat(only(audit("alice", "WRITE,INSERT,TABLE,crm.orders,INSERT INTO crm.orders VALUES (1)"), null)).containsEntry("accessType", "insert");
        assertThat(only(audit("alice", "WRITE,UPDATE,TABLE,crm.orders,MERGE INTO crm.orders o USING s ON true"), null)).containsEntry("accessType",
                "update");
        assertThat(only(audit("alice", "WRITE,SELECT,TABLE,crm.orders,SELECT 1 FROM crm.orders FOR UPDATE"), null)).containsEntry("accessType",
                "update");
        assertThat(only(audit("alice", "WRITE,DELETE,TABLE,crm.orders,DELETE FROM crm.orders"), null)).containsEntry("accessType", "delete");
        assertThat(only(audit("alice", "WRITE,COPY,TABLE,crm.orders,COPY crm.orders FROM STDIN"), null)).containsEntry("accessType", "insert");
        assertThat(only(audit("alice", "READ,SELECT,TABLE,crm.orders,COPY crm.orders TO STDOUT"), null)).containsEntry("accessType", "select");
        assertThat(only(audit("alice", "READ,SELECT,VIEW,\"crm.\"\"Regions\"\"\",SELECT * FROM crm.\"Regions\""), null))
                .as("pgaudit quotes what needs quoting, as a CSV field").containsEntry("resource", "sales.crm.Regions");
        assertThat(events(audit("alice", "WRITE,REFRESH MATERIALIZED VIEW,MATERIALIZED VIEW,crm.m,x"), null)).isEmpty();
        assertThat(events(audit("alice", "READ,SELECT,TABLE,pg_catalog.pg_class,x"), null)).as("PostgreSQL's own tables").isEmpty();
        assertThat(events(audit("alice", "READ,SELECT,INDEX,crm.orders_pkey,x"), null)).isEmpty();
        assertThat(events(audit("alice", "READ,SELECT,,,SELECT 1"), null)).isEmpty();
    }

    @Test
    void readsTheTablesOfATruncate()
    {
        List<AccessEvent> events = events(audit("alice", "WRITE,TRUNCATE TABLE,,,\"TRUNCATE TABLE ONLY crm.orders, \"\"crm\"\".\"\"Odd\"\" *, regions"
                + " RESTART IDENTITY\""), null);
        assertThat(events).extracting(event -> EventFields.of(event).get("resource")).containsExactly("sales.crm.orders", "sales.crm.Odd",
                "sales.crm.regions");
        assertThat(events(audit("alice", "WRITE,TRUNCATE TABLE,,,DELETE FROM x"), null)).isEmpty();
    }

    @Test
    void readsWhatDdlCreated()
    {
        assertThat(only(audit("alice", "DDL,CREATE TABLE,,,CREATE TABLE IF NOT EXISTS Crm.T (x int)"), null)).containsEntry("resource", "sales.crm")
                .containsEntry("accessType", "create").containsEntry("resourceType", "schema");
        assertThat(only(audit("alice", "DDL,CREATE VIEW,,,CREATE OR REPLACE VIEW v AS SELECT 1"), null)).containsEntry("resource", "sales.public");
        assertThat(only(audit("alice", "DDL,CREATE TABLE,,,CREATE TEMP TABLE t (x int)"), null)).containsEntry("accessType", "temporary")
                .containsEntry("resource", "sales");
        assertThat(only(audit("alice", "DDL,CREATE SCHEMA,,,CREATE SCHEMA team"), null)).containsEntry("accessType", "create_schema");
        assertThat(only(audit("alice", "DDL,CREATE TRIGGER,,,CREATE TRIGGER t BEFORE INSERT ON crm.orders EXECUTE FUNCTION f()"), null))
                .containsEntry("accessType", "trigger").containsEntry("resource", "sales.crm.orders");
        assertThat(events(audit("alice", "DDL,CREATE TRIGGER,,,CREATE TRIGGER t BEFORE INSERT"), null)).isEmpty();
        assertThat(events(audit("alice", "DDL,CREATE TABLE,,,CREATE TABLE pg_temp.t (x int)"), null)).isEmpty();
        assertThat(events(audit("alice", "DDL,ALTER TABLE,,,ALTER TABLE crm.orders ADD x int"), null)).isEmpty();
        assertThat(events(audit("alice", "ROLE,GRANT,,,GRANT x TO y"), null)).isEmpty();
    }

    @Test
    void reportsWhatPostgreSqlRefusedForAMissingPrivilege()
    {
        PostgreSqlSnapshots policies = new PostgreSqlSnapshots().allow(List.of("bob"), List.of("select"), column("sales", "crm", "orders", "*"));
        long card = policies.nextId();
        Snapshot snapshot = policies.deny(List.of("bob"), List.of("select"), column("sales", "crm", "orders", "card")).snapshot();
        Map<String, @Nullable Object> bob = only(entry("bob", "ERROR", "42501", "SELECT", "permission denied for table orders",
                "SELECT card FROM crm.orders"), snapshot);
        assertThat(bob).containsEntry("resource", "sales.crm.orders").containsEntry("outcome", "DENIED").containsEntry("enforcer", "GRANTFORGE")
                .containsEntry("policyId", Long.toString(card));
        Snapshot nothing = new PostgreSqlSnapshots().snapshot();
        assertThat(only(entry("bob", "ERROR", "42501", "SELECT", "permission denied for table orders", "SELECT 1 FROM crm.orders"), nothing))
                .containsEntry("enforcer", "GRANTFORGE");
        assertThat(only(entry("bob", "ERROR", "42501", "INSERT", "permission denied for table orders", "INSERT INTO crm.orders SELECT 1"), null))
                .containsEntry("accessType", "insert");
        assertThat(only(entry("bob", "ERROR", "42501", "INSERT", "permission denied for table orders",
                "INSERT INTO crm.items SELECT * FROM crm.orders"), null)).as("the table read").containsEntry("accessType", "select");
        assertThat(only(entry("bob", "ERROR", "42501", "DELETE", "permission denied for table orders", "DELETE FROM ONLY crm.orders"), null))
                .containsEntry("accessType", "delete");
        assertThat(only(entry("bob", "ERROR", "42501", "UPDATE", "permission denied for table orders", "UPDATE crm.orders SET x = 1"), null))
                .containsEntry("accessType", "update");
        assertThat(only(entry("bob", "ERROR", "42501", "TRUNCATE TABLE", "permission denied for table orders", "TRUNCATE crm.orders"), null))
                .containsEntry("accessType", "truncate");
        assertThat(only(entry("bob", "ERROR", "42501", "CREATE TRIGGER", "permission denied for table orders",
                "CREATE TRIGGER t AFTER INSERT ON crm.orders EXECUTE FUNCTION f()"), null)).containsEntry("accessType", "trigger");
        assertThat(only(entry("bob", "ERROR", "42501", "COPY", "permission denied for table orders", "COPY crm.orders FROM STDIN"), null))
                .containsEntry("accessType", "insert");
        assertThat(only(entry("bob", "ERROR", "42501", "COPY", "permission denied for table orders", "COPY crm.orders TO STDOUT"), null))
                .containsEntry("accessType", "select");
        assertThat(only(entry("bob", "ERROR", "42501", "INSERT", "permission denied for sequence orders_id_seq", "INSERT INTO crm.orders VALUES (1)"),
                null)).as("the insert a serial column's sequence refused").containsEntry("accessType", "insert").containsEntry("resource", "sales.crm.orders");
        assertThat(events(entry("bob", "ERROR", "42501", "INSERT", "permission denied for sequence other_seq", "x"), null)).isEmpty();
    }

    @Test
    void reportsRefusalsOfSchemasAndDatabases()
    {
        Snapshot snapshot = new PostgreSqlSnapshots().allow(List.of("bob"), List.of("select"), column("sales", "crm", "orders", "*")).snapshot();
        assertThat(only(entry("bob", "ERROR", "42501", "CREATE TABLE", "permission denied for schema crm", "CREATE TABLE crm.x (i int)"), snapshot))
                .containsEntry("accessType", "create").containsEntry("resource", "sales.crm").containsEntry("enforcer", "GRANTFORGE");
        assertThat(only(entry("bob", "ERROR", "42501", "SELECT", "permission denied for schema hidden", "SELECT * FROM hidden.t"), snapshot))
                .containsEntry("accessType", "usage").containsEntry("resourceType", "schema").containsEntry("enforcer", "GRANTFORGE");
        assertThat(events(entry("bob", "ERROR", "42501", "SELECT", "permission denied for schema pg_toast", "x"), null)).isEmpty();
        assertThat(only(entry("bob", "FATAL", "42501", "startup", "permission denied for database \"sales\"", null), snapshot))
                .containsEntry("accessType", "connect").containsEntry("resource", "sales").containsEntry("outcome", "DENIED")
                .containsEntry("enforcer", "NATIVE").containsEntry("request", null);
        assertThat(only(entry("bob", "ERROR", "42501", "CREATE SCHEMA", "permission denied for database sales", "CREATE SCHEMA s"), null))
                .containsEntry("accessType", "create_schema");
        assertThat(events(entry("bob", "ERROR", "42501", "CREATE SCHEMA", "permission denied for database other", "CREATE SCHEMA s"), null)).isEmpty();
        assertThat(only(entry("bob", "ERROR", "42501", "CREATE TABLE", "permission denied to create temporary tables in database \"sales\"",
                "CREATE TEMP TABLE t (x int)"), null)).containsEntry("accessType", "temporary");
        assertThat(events(entry("bob", "ERROR", "42501", "SELECT", "permission denied to set role \"x\"", "SET ROLE x"), null)).isEmpty();
    }

    @Test
    void findsTheSchemaOfATableAMessageNamesAlone()
    {
        Inventory twice = Inventory.of(List.of(ServerStateTest.column("crm", "orders", "r", "id", null), ServerStateTest.column("public", "orders", "r",
                "id", null), ServerStateTest.column("crm", "items", "r", "id", null), ServerStateTest.column("x", "items", "r", "id", null)));
        assertThat(ServerLogAudit.resolve(List.of("orders"), "SELECT * FROM crm.orders", twice)).isEqualTo(new TableRef("crm", "orders"));
        assertThat(ServerLogAudit.resolve(List.of("orders"), "SELECT * FROM orders", twice)).as("public").isEqualTo(new TableRef("public", "orders"));
        assertThat(ServerLogAudit.resolve(List.of("items"), "SELECT * FROM items", twice)).as("cannot tell").isNull();
        assertThat(ServerLogAudit.resolve(List.of("orders"), "SELECT * FROM \"Other\".orders", twice)).isEqualTo(new TableRef("public", "orders"));
        assertThat(ServerLogAudit.resolve(List.of("crm", "orders"), "", twice)).isEqualTo(new TableRef("crm", "orders"));
        assertThat(ServerLogAudit.resolve(List.of("information_schema", "tables"), "", twice)).isNull();
        assertThat(ServerLogAudit.parts("\"Crm\" . Orders")).containsExactly("Crm", "orders");
        assertThat(ServerLogAudit.parts("\"a\"\"b\"")).containsExactly("a\"b");
    }

    @Test
    void leavesOutWhatTellsOfNoAccess()
    {
        assertThat(events(audit("grantforge", "READ,SELECT,TABLE,crm.orders,x"), null)).as("the agent's own").isEmpty();
        assertThat(events(entry("alice", "LOG", null, "idle", "connection authorized", null), null)).isEmpty();
        assertThat(events(entry("alice", "ERROR", "42P01", "SELECT", "relation \"x\" does not exist", "SELECT 1"), null)).isEmpty();
        assertThat(events(entry("alice", "ERROR", "42501", "SELECT", "must be owner of table orders", "x"), null)).isEmpty();
        assertThat(events(entry("alice", "LOG", null, "SELECT", "AUDIT: OBJECT,1,1,READ,SELECT,TABLE,crm.orders,x,<none>", null), null)).isEmpty();
        assertThat(events(entry("alice", "LOG", null, "SELECT", "AUDIT: SESSION,1", null), null)).isEmpty();
        ObjectNode background = audit("alice", "READ,SELECT,TABLE,crm.orders,x").put("backend_type", "autovacuum worker");
        assertThat(events(background, null)).isEmpty();
        ObjectNode local = audit("alice", "READ,SELECT,TABLE,crm.orders,x").put("remote_host", "[local]");
        assertThat(only(local, null)).containsEntry("clientIp", null);
        ObjectNode anonymous = audit("alice", "READ,SELECT,TABLE,crm.orders,x");
        anonymous.remove("user");
        assertThat(events(anonymous, null)).isEmpty();
    }

    @Test
    void readsTimestampsAndCsv()
    {
        assertThat(ServerLogAudit.instant("2026-10-26 02:17:31.835 UTC")).isEqualTo(Instant.parse("2026-10-26T02:17:31.835Z"));
        assertThat(ServerLogAudit.instant("2026-07-26 04:17:31.835 CEST")).isEqualTo(Instant.parse("2026-07-26T02:17:31.835Z"));
        Instant before = Instant.now();
        assertThat(ServerLogAudit.instant("yesterday")).isAfterOrEqualTo(before);
        assertThat(ServerLogAudit.csv("a,\"b,\"\"c\"\"\",")).containsExactly("a", "b,\"c\"", "");
        assertThat(ServerLogAudit.csv("")).isEmpty();
        assertThat(ServerLogAudit.csv("\"unterminated")).isEmpty();
    }

    /** A server log of the files given, read by offset and length as pg_read_binary_file reads. */
    private static FakePostgreSql logOf(AtomicReference<Map<String, byte[]>> files, String destination, String preload)
    {
        return new FakePostgreSql().answer(null, "current_setting('log_destination')", parameters -> List.of(Row.of("self", "grantforge", "destination",
                destination, "collector", "on", "directory", "log", "preload", preload)))
                .answer(null, "FROM pg_ls_logdir()", parameters -> {
                    List<Row> rows = new ArrayList<>();
                    for (Map.Entry<String, byte[]> file : requireNonNull(files.get(), "files").entrySet()) {
                        // Files are written in the order of their names.
                        rows.add(Row.of("name", file.getKey(), "size", (long) file.getValue().length, "modified", (long) file.getKey().charAt(0)));
                    }
                    return rows;
                })
                .answer(null, "pg_read_binary_file", parameters -> {
                    String name = String.valueOf(parameters.get(0)).substring("log/".length());
                    byte[] whole = requireNonNull(files.get(), "files").getOrDefault(name, new byte[0]);
                    int from = (int) (long) (Long) parameters.get(1);
                    int length = (int) (long) (Long) parameters.get(2);
                    return List.of(Row.of("bytes", Arrays.copyOfRange(whole, Math.min(from, whole.length), Math.min(from + length, whole.length))));
                });
    }

    private static byte[] lines(JsonNode... entries)
    {
        StringBuilder text = new StringBuilder();
        for (JsonNode entry : entries) {
            text.append(entry).append('\n');
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] concat(byte[] first, byte[] second)
    {
        byte[] both = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        return both;
    }

    @Test
    void readsTheLogFromWhereTheLastReadEndedAcrossFilesAndRestarts() throws IOException
    {
        byte[] earlier = lines(audit("alice", "READ,SELECT,TABLE,crm.orders,old"));
        AtomicReference<Map<String, byte[]>> files = new AtomicReference<>(new java.util.LinkedHashMap<>(Map.of("a.json", earlier)));
        FakePostgreSql postgres = logOf(files, "stderr,jsonlog", "pgaudit");
        List<AccessEvent> events = new ArrayList<>();
        Path cursor = directory.resolve("cache/position");
        ServerLogAudit audit = new ServerLogAudit(postgres, "postgres", new AgentConfig.ServerLog(Duration.ofSeconds(1), 1 << 20), cursor, () -> null,
                user -> Set.of(), database -> SALES, events::add);
        assertThat(audit.poll()).as("the first read starts at the present").isZero();
        assertThat(Files.readString(cursor)).startsWith("a.json\t" + earlier.length + "\t");

        byte[] partial = "{\"user\":\"ali".getBytes(StandardCharsets.UTF_8);
        byte[] more = concat(concat(earlier, lines(audit("alice", "READ,SELECT,TABLE,crm.orders,new"))), partial);
        files.set(new java.util.LinkedHashMap<>(Map.of("a.json", more)));
        assertThat(audit.poll()).isEqualTo(1);
        assertThat(events).hasSize(1);

        java.util.LinkedHashMap<String, byte[]> rotated = new java.util.LinkedHashMap<>();
        rotated.put("a.json", concat(Arrays.copyOf(more, more.length - partial.length), lines(audit("bob", "READ,SELECT,TABLE,crm.orders,end"))));
        rotated.put("b.json", lines(audit("carol", "READ,SELECT,TABLE,crm.orders,next"), JSON.createObjectNode().put("message", "x")));
        files.set(rotated);
        ServerLogAudit restarted = new ServerLogAudit(postgres, "postgres", new AgentConfig.ServerLog(Duration.ofSeconds(1), 1 << 20), cursor,
                () -> null, user -> Set.of(), database -> SALES, events::add);
        assertThat(restarted.poll()).isEqualTo(3);
        assertThat(events).extracting(event -> EventFields.of(event).get("user")).containsExactly("alice", "bob", "carol");

        files.set(new java.util.LinkedHashMap<>(Map.of("c.json", lines(audit("dave", "READ,SELECT,TABLE,crm.orders,after")))));
        assertThat(restarted.poll()).as("the file read last is gone: the next ones are read").isEqualTo(1);
        files.set(new java.util.LinkedHashMap<>(Map.of("c.json", lines(audit("erin", "READ,SELECT,TABLE,crm.orders,x")))));
        assertThat(restarted.poll()).as("a file shorter than read before was written again and is read from its start").isEqualTo(1);
        assertThat(events).extracting(event -> EventFields.of(event).get("user")).endsWith("dave", "erin");
    }

    @Test
    void readsNoMoreThanItMayAtATimeAndSkipsALineTooLong() throws IOException
    {
        byte[] line = lines(audit("alice", "READ,SELECT,TABLE,crm.orders,x"));
        AtomicReference<Map<String, byte[]>> files = new AtomicReference<>(new java.util.LinkedHashMap<>(Map.of("a.json", new byte[0])));
        FakePostgreSql postgres = logOf(files, "jsonlog", "pgaudit");
        List<AccessEvent> events = new ArrayList<>();
        ServerLogAudit audit = new ServerLogAudit(postgres, "postgres", new AgentConfig.ServerLog(Duration.ofSeconds(1), line.length),
                directory.resolve("position"), () -> null, user -> Set.of(), database -> SALES, events::add);
        audit.poll();
        files.set(new java.util.LinkedHashMap<>(Map.of("a.json", concat(line, line))));
        assertThat(audit.poll()).isEqualTo(1);
        assertThat(audit.poll()).isEqualTo(1);

        byte[] huge = new byte[ServerLogAudit.CHUNK + 10];
        Arrays.fill(huge, (byte) 'x');
        huge[huge.length - 1] = '\n';
        files.set(new java.util.LinkedHashMap<>(Map.of("a.json", concat(concat(line, line), concat(huge, line)))));
        ServerLogAudit wide = new ServerLogAudit(postgres, "postgres", new AgentConfig.ServerLog(Duration.ofSeconds(1), 4L << 20),
                directory.resolve("position"), () -> null, user -> Set.of(), database -> SALES, events::add);
        assertThat(wide.poll()).as("the line too long is skipped, partly as no JSON").isEqualTo(2);
        assertThat(events).hasSize(3);
    }

    @Test
    void readsNothingWithoutAJsonLogOrAnyFile() throws IOException
    {
        AtomicReference<Map<String, byte[]>> files = new AtomicReference<>(Map.of());
        ServerLogAudit plain = new ServerLogAudit(logOf(files, "stderr", ""), "postgres", new AgentConfig.ServerLog(Duration.ofSeconds(1), 1 << 20),
                directory.resolve("position"), () -> null, user -> Set.of(), database -> SALES, event -> { });
        assertThat(plain.poll()).isZero();
        ServerLogAudit empty = new ServerLogAudit(logOf(files, "jsonlog", ""), "postgres", new AgentConfig.ServerLog(Duration.ofSeconds(1), 1 << 20),
                directory.resolve("position"), () -> null, user -> Set.of(), database -> SALES, event -> { });
        assertThat(empty.poll()).isZero();
        Files.writeString(directory.resolve("broken"), "a.json\tnot a number");
        files.set(Map.of("a.json", new byte[3]));
        ServerLogAudit broken = new ServerLogAudit(logOf(files, "jsonlog", ""), "postgres", new AgentConfig.ServerLog(Duration.ofSeconds(1), 1 << 20),
                directory.resolve("broken"), () -> null, user -> Set.of(), database -> SALES, event -> { });
        assertThat(broken.poll()).as("an unreadable position starts at the present").isZero();
        Path blocked = Files.writeString(directory.resolve("blocked"), "");
        ServerLogAudit unwritable = new ServerLogAudit(logOf(files, "jsonlog", ""), "postgres", new AgentConfig.ServerLog(Duration.ofSeconds(1), 1 << 20),
                blocked.resolve("position"), () -> null, user -> Set.of(), database -> SALES, event -> { });
        assertThat(unwritable.poll()).as("a position that cannot be kept is only logged").isZero();
    }
}
