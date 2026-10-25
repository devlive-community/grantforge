// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import com.clickhouse.client.api.sql.SQLUtils;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports accesses from ClickHouse's query log: each statement of a user the log shows, as one event per table, database
 * or the server it touched, with what ClickHouse decided. A statement ClickHouse refused for a missing privilege is one
 * denied event for the privilege its error names. Each event says whether GrantForge decided it, that is whether the
 * policies decide as ClickHouse did; an access ClickHouse allows through a grant made outside GrantForge is the
 * system's own decision. The log is read some seconds behind the present, as ClickHouse writes it in batches, and from
 * where the last read ended, which survives restarts; the first read starts at the present.
 */
final class QueryLogAudit
{
    private static final Logger LOG = Logger.getLogger(QueryLogAudit.class.getName());

    /** What a refusal for a missing privilege names: the privilege, with its columns, and where it is needed. */
    private static final Pattern NEEDED = Pattern.compile("necessary to have the grant (.+?) ON (.+?)(?: WITH GRANT OPTION)?\\. \\(ACCESS_DENIED\\)");
    private static final Pattern PRIVILEGE = Pattern.compile("([A-Za-z][A-Za-z ]*?)\\s*(?:\\((.*)\\))?");
    private static final Pattern LITERAL = Pattern.compile("[0-9A-Fa-f:.]+");
    private static final Pattern CAMEL = Pattern.compile("(?<=[a-z])(?=[A-Z])");
    private static final Pattern TRUNCATE = Pattern.compile("^\\s*TRUNCATE\\b", Pattern.CASE_INSENSITIVE);

    private final ClickHouse clickhouse;
    private final AgentConfig.QueryLog settings;
    private final Path cursorFile;
    private final Supplier<@Nullable Snapshot> snapshots;
    private final Function<String, Set<String>> groups;
    private final Function<TableRef, List<String>> columns;
    private final Consumer<AccessEvent> sink;
    private @Nullable Cursor cursor;

    /**
     * Where a read ended: the moment of the last entry read, in microseconds, and its query.
     *
     * @param micros the moment
     * @param queryId the query
     */
    record Cursor(long micros, String queryId)
    {
    }

    /**
     * Reads the log.
     *
     * @param clickhouse the server
     * @param settings how
     * @param cursorFile where the read position is kept
     * @param snapshots the policies, to say who decided
     * @param groups the ClickHouse roles each user holds, which policies name as groups
     * @param columns the columns of a table
     * @param sink where the events go
     */
    QueryLogAudit(ClickHouse clickhouse, AgentConfig.QueryLog settings, Path cursorFile, Supplier<@Nullable Snapshot> snapshots,
            Function<String, Set<String>> groups, Function<TableRef, List<String>> columns, Consumer<AccessEvent> sink)
    {
        this.clickhouse = clickhouse;
        this.settings = settings;
        this.cursorFile = cursorFile;
        this.snapshots = snapshots;
        this.groups = groups;
        this.columns = columns;
        this.sink = sink;
    }

    /**
     * Reads what was logged since the last read, batch by batch.
     *
     * @return how many entries were read
     * @throws ClickHouseFailure when the server fails the query
     */
    // A cursor per entry read is how reading moves on.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    int poll()
    {
        Cursor from = cursor == null ? start() : cursor;
        cursor = from;
        int read = 0;
        while (true) {
            List<Row> rows = clickhouse.query(query(), Map.of("micros", from.micros(), "id", from.queryId(), "delay",
                    settings.delay().toMillis(), "limit", settings.batch()));
            for (Row row : rows) {
                for (AccessEvent event : events(row, snapshots.get(), groups, columns)) {
                    sink.accept(event);
                }
                from = new Cursor(row.number("micros"), row.string("query_id"));
            }
            read += rows.size();
            if (!rows.isEmpty()) {
                cursor = from;
                save(from);
            }
            if (rows.size() < settings.batch()) {
                return read;
            }
        }
    }

    private String query()
    {
        String log = settings.cluster() == null ? "system.query_log"
                : "clusterAllReplicas(" + SQLUtils.enquoteLiteral(settings.cluster()) + ", system.query_log)";
        return "SELECT toUnixTimestamp64Micro(event_time_microseconds) AS micros, query_id, toString(type) AS type, user,"
                + " toString(initial_address) AS address, query_kind, databases, tables, columns, exception_code, exception, query FROM " + log
                + " WHERE event_date >= toDate(fromUnixTimestamp64Micro({micros:Int64})) AND is_initial_query AND type != 'QueryStart'"
                + " AND user != currentUser() AND (toUnixTimestamp64Micro(event_time_microseconds), query_id) > ({micros:Int64}, {id:String})"
                + " AND event_time_microseconds <= now64(6) - toIntervalMillisecond({delay:Int64})"
                + " ORDER BY event_time_microseconds, query_id LIMIT {limit:UInt32}";
    }

    /** Where the first read starts: where the last one ended, or the present. */
    private Cursor start()
    {
        try {
            if (Files.exists(cursorFile)) {
                String[] saved = Files.readString(cursorFile, StandardCharsets.UTF_8).strip().split("\t", 2);
                return new Cursor(Long.parseLong(saved[0]), saved.length > 1 ? saved[1] : "");
            }
        }
        catch (IOException | NumberFormatException unreadable) {
            LOG.log(Level.WARNING, "The query log position in " + cursorFile + " cannot be read; reading starts at the present", unreadable);
        }
        long micros = clickhouse.query("SELECT toUnixTimestamp64Micro(now64(6)) AS micros", Map.of()).get(0).number("micros");
        Cursor present = new Cursor(micros - settings.delay().toNanos() / 1000, "");
        save(present);
        return present;
    }

    private void save(Cursor at)
    {
        try {
            Path directory = cursorFile.toAbsolutePath().getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            Path written = cursorFile.resolveSibling(cursorFile.getFileName() + ".tmp");
            Files.writeString(written, at.micros() + "\t" + at.queryId(), StandardCharsets.UTF_8);
            try {
                Files.move(written, cursorFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException plain) {
                Files.move(written, cursorFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException unwritable) {
            LOG.log(Level.WARNING, "The query log position cannot be kept in " + cursorFile + "; a restart reads from the present", unwritable);
        }
    }

    /**
     * Turns one entry of the log into events.
     *
     * @param row the entry
     * @param snapshot the policies, or {@code null} before the agent has any
     * @param groups the ClickHouse roles of each user
     * @param columns the columns of a table, for a refusal that names the table where the user may read some columns
     * @return the events, none for a statement that touches nothing the policies decide
     */
    static List<AccessEvent> events(Row row, @Nullable Snapshot snapshot, Function<String, Set<String>> groups,
            Function<TableRef, List<String>> columns)
    {
        String user = row.string("user");
        String kind = row.string("query_kind");
        Instant at = Instant.EPOCH.plus(row.number("micros"), ChronoUnit.MICROS);
        String address = address(row.string("address"));
        String query = row.text("query");
        List<Access> accesses;
        boolean allowed;
        if (row.number("exception_code") == ClickHouseFailure.ACCESS_DENIED) {
            allowed = false;
            accesses = denied(row.string("exception"), kind, row);
        }
        else if ("QueryFinish".equals(row.string("type")) || "ExceptionWhileProcessing".equals(row.string("type"))) {
            allowed = true;
            accesses = accesses(kind, row);
        }
        else {
            return List.of();
        }
        List<AccessEvent> events = new ArrayList<>(accesses.size());
        Set<String> held = groups.apply(user);
        for (Access access : accesses) {
            AccessEvent.Builder event = AccessEvent.builder(user, access.resource(), access.type(), allowed).occurredAt(at).clientIp(address)
                    .resourceType(access.level()).action(CAMEL.matcher(kind).replaceAll(" ").toUpperCase(Locale.ROOT)).request(query);
            if (snapshot != null && access.level() != null) {
                AgentDecision decision = allowed ? access.decide(snapshot, user, held, at) : access.refused(snapshot, user, held, at, columns);
                if (decision.allowed() == allowed) {
                    event.decidedBy(decision).enforcedByGrantForge();
                }
            }
            events.add(event.build());
        }
        return events;
    }

    /**
     * One access of a statement.
     *
     * @param type the access type
     * @param level the lowest level named, or {@code null} when the log does not say what was touched
     * @param database the database, or {@code null} for the server
     * @param table the table, or {@code null}
     * @param columns the columns read or refused, none for the table as a whole
     */
    record Access(String type, @Nullable String level, @Nullable String database, @Nullable String table, List<String> columns)
    {
        Access
        {
            columns = List.copyOf(columns);
        }

        String resource()
        {
            if (level == null || database == null) {
                return "*";
            }
            if (table == null) {
                return database;
            }
            return database + "." + table + ("column".equals(level) ? "." + columns.get(0) : "");
        }

        /** What the policies decide of it: each column read must be allowed, or the table when none is named. */
        AgentDecision decide(Snapshot snapshot, String user, Set<String> groups, Instant at)
        {
            if (database == null) {
                return snapshot.decide(request(user, groups, at).resource("server", "*").build());
            }
            AccessRequest.Builder base = request(user, groups, at).resource("database", database);
            if (table == null) {
                return snapshot.decide(base.build());
            }
            base.resource("table", table);
            if (columns.isEmpty()) {
                return snapshot.decide(base.below(Privileges.COLUMN_TYPES.contains(type)).build());
            }
            AgentDecision first = column(snapshot, user, groups, at, columns.get(0));
            if (!first.allowed()) {
                return first;
            }
            for (String column : columns.subList(1, columns.size())) {
                AgentDecision decision = column(snapshot, user, groups, at, column);
                if (!decision.allowed()) {
                    return decision;
                }
            }
            return first;
        }

        /**
         * What the policies decide of an access ClickHouse refused. ClickHouse may name a table where the user lacks one
         * of its columns; the policies refuse it when they refuse the table or one of its columns.
         */
        AgentDecision refused(Snapshot snapshot, String user, Set<String> groups, Instant at, Function<TableRef, List<String>> columnsOf)
        {
            if (database == null || table == null || !columns.isEmpty() || !Privileges.COLUMN_TYPES.contains(type)) {
                return decide(snapshot, user, groups, at);
            }
            AgentDecision whole = snapshot.decide(request(user, groups, at).resource("database", database).resource("table", table).build());
            if (!whole.allowed()) {
                return whole;
            }
            for (String column : columnsOf.apply(new TableRef(database, table))) {
                AgentDecision decision = column(snapshot, user, groups, at, column);
                if (!decision.allowed()) {
                    return decision;
                }
            }
            return whole;
        }

        private AgentDecision column(Snapshot snapshot, String user, Set<String> groups, Instant at, String column)
        {
            return snapshot.decide(request(user, groups, at).resource("database", String.valueOf(database)).resource("table", String.valueOf(table))
                    .resource("column", column).build());
        }

        private AccessRequest.Builder request(String user, Set<String> groups, Instant at)
        {
            return AccessRequest.builder(user, type).groups(groups.toArray(new String[0])).time(at);
        }
    }

    /** What a statement ClickHouse ran touched, by its kind. */
    static List<Access> accesses(String kind, Row row)
    {
        List<String> tables = row.texts("tables");
        List<String> read = row.texts("columns");
        List<Access> accesses = new ArrayList<>();
        switch (kind) {
            case "Select" -> tables.forEach(name -> accesses.add(table(Privileges.SELECT, name, read)));
            case "Insert" -> {
                for (String name : tables) {
                    List<String> columns = columnsOf(name, read);
                    accesses.add(columns.isEmpty() ? table(Privileges.INSERT, name, List.of()) : table(Privileges.SELECT, name, read));
                }
            }
            case "Alter", "Delete", "Update" -> tables.forEach(name -> accesses.add(table(Privileges.ALTER, name, List.of())));
            case "Optimize" -> tables.forEach(name -> accesses.add(table(Privileges.OPTIMIZE, name, List.of())));
            case "Drop" -> {
                String type = TRUNCATE.matcher(row.string("query")).find() ? Privileges.TRUNCATE : Privileges.DROP;
                if (tables.isEmpty()) {
                    row.texts("databases").forEach(database -> accesses.add(database(Privileges.DROP_DATABASE, database)));
                }
                else {
                    tables.forEach(name -> accesses.add(table(type, name, List.of())));
                }
            }
            case "Create" -> {
                if (tables.isEmpty()) {
                    if (!row.texts("databases").isEmpty()) {
                        accesses.add(server(Privileges.CREATE_DATABASE));
                    }
                }
                else {
                    Set<String> databases = new LinkedHashSet<>();
                    tables.forEach(name -> databases.add(split(name).get(0)));
                    databases.forEach(database -> accesses.add(database(Privileges.CREATE, database)));
                }
            }
            case "System" -> accesses.add(server(Privileges.SYSTEM));
            case "KillQuery" -> accesses.add(server(Privileges.KILL_QUERY));
            default -> {
                // Statements of other kinds, such as SHOW or GRANT, touch nothing the policies decide.
            }
        }
        return accesses;
    }

    /** What a refusal for a missing privilege names; a refusal it cannot read is one access of the statement's kind. */
    static List<Access> denied(String exception, String kind, Row row)
    {
        Matcher needed = NEEDED.matcher(exception);
        if (needed.find()) {
            Matcher privilege = PRIVILEGE.matcher(needed.group(1).strip());
            List<String> target = split(needed.group(2).strip());
            if (privilege.matches() && target.size() == 2) {
                Set<String> types = Privileges.accessTypes(privilege.group(1));
                String type = types.isEmpty() ? privilege.group(1).strip().toLowerCase(Locale.ROOT).replace(' ', '_') : types.iterator().next();
                List<String> columns = privilege.group(2) == null ? List.of() : split(privilege.group(2), ',');
                String database = target.get(0);
                String table = target.get(1);
                if ("*".equals(database)) {
                    return List.of(server(type));
                }
                if ("*".equals(table)) {
                    return List.of(database(type, database));
                }
                return List.of(new Access(type, columns.size() == 1 ? "column" : "table", database, table, columns));
            }
        }
        List<Access> accesses = accesses(kind, row);
        if (!accesses.isEmpty()) {
            return accesses;
        }
        return List.of(new Access(kind.toLowerCase(Locale.ROOT), null, null, null, List.of()));
    }

    private static Access table(String type, String qualified, List<String> read)
    {
        List<String> name = split(qualified);
        String database = name.get(0);
        String table = name.size() > 1 ? name.get(1) : "";
        return new Access(type, "table", database, table, columnsOf(qualified, read));
    }

    private static Access database(String type, String database)
    {
        return new Access(type, "database", database, null, List.of());
    }

    private static Access server(String type)
    {
        return new Access(type, "server", null, null, List.of());
    }

    /** The columns of one table among those a statement read, named as table.column. */
    private static List<String> columnsOf(String table, List<String> read)
    {
        List<String> columns = new ArrayList<>();
        String start = table + ".";
        for (String column : read) {
            if (column.startsWith(start)) {
                columns.add(column.substring(start.length()));
            }
        }
        return columns;
    }

    /** Splits a qualified name at its dots, as ClickHouse writes it: parts in backquotes are taken as they are. */
    static List<String> split(String qualified)
    {
        return split(qualified, '.');
    }

    private static List<String> split(String text, char separator)
    {
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char next = text.charAt(index);
            if (escaped) {
                part.append(next);
                escaped = false;
            }
            else if (quoted && next == '\\') {
                escaped = true;
            }
            else if (next == '`') {
                quoted = !quoted;
            }
            else if (next == separator && !quoted) {
                parts.add(part.toString().strip());
                part.setLength(0);
            }
            else {
                part.append(next);
            }
        }
        parts.add(part.toString().strip());
        return parts;
    }

    /** A client address as the log writes it, with IPv4 addresses that IPv6 carries written as IPv4. */
    static @Nullable String address(String logged)
    {
        if (logged.isEmpty()) {
            return null;
        }
        if (!LITERAL.matcher(logged).matches()) {
            return logged;
        }
        try {
            // A literal address, so nothing is looked up.
            return InetAddress.getByName(logged).getHostAddress();
        }
        catch (UnknownHostException | SecurityException unreadable) {
            return logged;
        }
    }
}
