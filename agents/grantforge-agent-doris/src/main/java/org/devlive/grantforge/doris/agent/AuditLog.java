// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.alibaba.druid.sql.ast.statement.SQLDropDatabaseStatement;
import com.alibaba.druid.sql.visitor.SchemaStatVisitor;
import com.alibaba.druid.stat.TableStat;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.agent.sync.Notices;
import org.devlive.grantforge.agent.sync.PositionFile;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * Reports accesses from Doris's audit log table, {@code __internal_schema.audit_log}, which every frontend writes in
 * batches: each statement of a user, as one event per table, database or catalog it touched, with what Doris decided.
 * The tables a statement touched are read from the statement itself with Druid's Doris parser, as the log names none; a
 * statement Doris refused for a missing privilege is one denied event for the privilege and object its error names.
 * Each event says whether GrantForge decided it, that is whether the policies decide as Doris did; an access Doris
 * allows through a grant made outside GrantForge is the system's own decision. The log is read behind the present by
 * the frontends' batch interval, {@code audit_plugin_max_batch_interval_sec}, and from where the last read ended, which
 * survives restarts; the first read starts at the present.
 */
final class AuditLog
{
    private static final Logger LOG = Logger.getLogger(AuditLog.class.getName());

    /** How the audit log writes moments, in the cluster's time zone. */
    static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);

    /** Doris's refusal that names the object: {@code ... [priv predicate: OR, Admin_priv,Select_priv] command on [internal].[sales].[orders]}. */
    private static final Pattern PERMISSION = Pattern.compile("priv predicate: \\w+, ([^\\]]+)] command on ((?:\\[[^\\]]*](?:\\.|$))+)");

    /** Doris's refusal that names only the privileges: {@code you need (at least one of) the (DROP) privilege(s)}. */
    private static final Pattern NEED = Pattern.compile("you need \\(at least one of\\) the \\(([^)]*)\\) privilege");

    /** Older releases' refusal of a table: {@code SELECT command denied to user 'u'@'%' for table 'sales.orders'}, or {@code 'sales: orders'}. */
    private static final Pattern COMMAND = Pattern.compile("(\\w+)(?: \\w+)? command denied to user .* for table '([^']*)'");

    /** A statement's first word, which names what it does. */
    private static final Pattern ACTION = Pattern.compile("^\\s*([A-Za-z]+)");

    /** Statements Druid does not read, by kind. */
    private static final Pattern ALTER = Pattern.compile("^\\s*ALTER\\s+(?:TABLE|VIEW)\\s+([`\\w.\\-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHOW_VIEW = Pattern.compile("^\\s*SHOW\\s+CREATE\\s+VIEW\\s+([`\\w.\\-]+)", Pattern.CASE_INSENSITIVE);

    private final Doris doris;
    private final AgentConfig.AuditLog settings;
    private final PositionFile cursorFile;
    private final Supplier<@Nullable Snapshot> snapshots;
    private final Function<String, Set<String>> groups;
    private final Function<TableRef, List<String>> columns;
    private final Consumer<AccessEvent> sink;
    private final Notices notices = new Notices(LOG);
    private @Nullable Cursor cursor;

    /**
     * Where a read ended: the moment of the last entry read, as the log writes it, and its query.
     *
     * @param time the moment
     * @param queryId the query
     */
    record Cursor(String time, String queryId)
    {
    }

    /**
     * Reads the log.
     *
     * @param doris the cluster
     * @param settings how
     * @param cursorFile where the read position is kept
     * @param snapshots the policies, to say who decided
     * @param groups the Doris roles each user holds, which policies name as groups
     * @param columns the columns of a table
     * @param sink where the events go
     */
    AuditLog(Doris doris, AgentConfig.AuditLog settings, Path cursorFile, Supplier<@Nullable Snapshot> snapshots, Function<String, Set<String>> groups,
            Function<TableRef, List<String>> columns, Consumer<AccessEvent> sink)
    {
        this.doris = doris;
        this.settings = settings;
        this.cursorFile = new PositionFile(cursorFile);
        this.snapshots = snapshots;
        this.groups = groups;
        this.columns = columns;
        this.sink = sink;
    }

    /**
     * Reads what was logged since the last read, batch by batch.
     *
     * @return how many entries were read
     * @throws DorisFailure when the cluster fails the query
     */
    // A cursor per entry read is how reading moves on.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    int poll()
    {
        Map<String, String> variables = variables();
        if (!"true".equalsIgnoreCase(variables.getOrDefault("enable_audit_plugin", "true"))) {
            notices.once(Level.WARNING, "Doris writes no audit log, so the agent reports no accesses; turn it on with SET GLOBAL enable_audit_plugin"
                    + " = true");
            return 0;
        }
        Row clock = doris.query("SELECT NOW(3) AS now, UTC_TIMESTAMP() AS utc, CURRENT_USER() AS self").get(0);
        LocalDateTime now = moment(clock, "now");
        ZoneOffset offset = offset(now, moment(clock, "utc"));
        String upper = MOMENT.format(now.minus(delay(variables)));
        String self = ServerState.name(clock.string("self"));
        Cursor from = cursor == null ? start(upper) : cursor;
        cursor = from;
        int read = 0;
        while (true) {
            List<Row> rows;
            try {
                rows = doris.query("SELECT `time`, query_id, `user`, client_ip, `catalog`, db, state, error_message, stmt"
                        + " FROM __internal_schema.audit_log WHERE `time` >= ? AND (`time` > ? OR query_id > ?) AND `time` <= ? AND `user` != ?"
                        + " ORDER BY `time`, query_id LIMIT ?", from.time(), from.time(), from.queryId(), upper, self, settings.batch());
            }
            catch (DorisFailure missing) {
                // A frontend creates the table some time after it starts with a backend; until then nothing was logged.
                if (missing.unreachable() || !String.valueOf(missing.getMessage()).contains("does not exist")) {
                    throw missing;
                }
                notices.once(Level.INFO, "Doris has no audit log table yet; the agent reads it once Doris creates it");
                return read;
            }
            for (Row row : rows) {
                for (AccessEvent event : events(row, offset, snapshots.get(), groups, columns)) {
                    sink.accept(event);
                }
                LocalDateTime time = row.moment("time");
                from = new Cursor(time == null ? from.time() : MOMENT.format(time), row.string("query_id"));
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

    /** The audit variables of the cluster. */
    private Map<String, String> variables()
    {
        Map<String, String> variables = new LinkedHashMap<>();
        for (Row row : doris.query("SHOW GLOBAL VARIABLES WHERE Variable_name IN ('enable_audit_plugin', 'audit_plugin_max_batch_interval_sec')")) {
            variables.put(row.string("Variable_name"), row.string("Value"));
        }
        return variables;
    }

    /**
     * How far behind the present the log is read: as set, or the frontends' batch interval and five seconds more.
     *
     * @param variables the cluster's audit variables
     * @return the delay
     */
    Duration delay(Map<String, String> variables)
    {
        Duration set = settings.delay();
        if (set != null) {
            return set;
        }
        String interval = variables.getOrDefault("audit_plugin_max_batch_interval_sec", "60");
        long seconds = interval.matches("\\d{1,6}") ? Long.parseLong(interval) : 60;
        return Duration.ofSeconds(seconds + 5);
    }

    private static LocalDateTime moment(Row row, String column)
    {
        LocalDateTime moment = row.moment(column);
        if (moment == null) {
            throw new DorisFailure("Doris returned no " + column + " time", false, null);
        }
        return moment;
    }

    /**
     * Returns the cluster's offset from UTC, to the quarter hour, so that the log's moments become instants.
     *
     * @param now the cluster's present moment
     * @param utc the same moment in UTC
     * @return the offset
     */
    static ZoneOffset offset(LocalDateTime now, LocalDateTime utc)
    {
        long minutes = Duration.between(utc, now).toMinutes();
        long quarters = Math.round(minutes / 15.0);
        return ZoneOffset.ofTotalSeconds((int) (quarters * 15 * 60));
    }

    /** Where the first read starts: where the last one ended, or the present. */
    private Cursor start(String present)
    {
        try {
            String text = cursorFile.read();
            if (text != null) {
                String[] saved = text.split("\t", 2);
                LocalDateTime.parse(saved[0], MOMENT);
                return new Cursor(saved[0], saved.length > 1 ? saved[1] : "");
            }
        }
        catch (IOException | RuntimeException unreadable) {
            LOG.log(Level.WARNING, "The audit log position in " + cursorFile.path() + " cannot be read; reading starts at the present", unreadable);
        }
        Cursor start = new Cursor(present, "");
        save(start);
        return start;
    }

    private void save(Cursor at)
    {
        try {
            cursorFile.write(at.time() + "\t" + at.queryId());
        }
        catch (IOException unwritable) {
            LOG.log(Level.WARNING, "The audit log position cannot be kept in " + cursorFile.path() + "; a restart reads from the present", unwritable);
        }
    }

    /**
     * Turns one entry of the log into events.
     *
     * @param row the entry
     * @param offset the cluster's offset from UTC
     * @param snapshot the policies, or {@code null} before the agent has any
     * @param groups the Doris roles of each user
     * @param columns the columns of a table, for a refusal that names a table where the user may read some columns
     * @return the events, none for a statement that touches nothing the policies decide or failed for another reason
     */
    static List<AccessEvent> events(Row row, ZoneOffset offset, @Nullable Snapshot snapshot, Function<String, Set<String>> groups,
            Function<TableRef, List<String>> columns)
    {
        String user = row.string("user");
        String statement = row.string("stmt");
        Matcher first = ACTION.matcher(statement);
        String kind = first.find() ? first.group(1).toUpperCase(Locale.ROOT) : "";
        LocalDateTime time = row.moment("time");
        Instant at = time == null ? Instant.EPOCH : time.toInstant(offset);
        String catalog = row.string("catalog").isEmpty() ? "internal" : row.string("catalog");
        String database = row.string("db");
        List<Access> accesses;
        boolean allowed;
        if ("ERR".equalsIgnoreCase(row.string("state"))) {
            accesses = denied(row.string("error_message"), statement, catalog, database);
            if (accesses.isEmpty()) {
                return List.of();
            }
            allowed = false;
        }
        else {
            allowed = true;
            accesses = accesses(statement, catalog, database);
        }
        List<AccessEvent> events = new ArrayList<>(accesses.size());
        Set<String> held = groups.apply(user);
        for (Access access : accesses) {
            AccessEvent.Builder event = AccessEvent.builder(user, access.resource(), access.type(), allowed).occurredAt(at)
                    .clientIp(address(row.string("client_ip"))).resourceType(access.level()).action(kind.isEmpty() ? null : kind).request(statement);
            if (snapshot != null) {
                AgentDecision decision = allowed ? access.decide(snapshot, user, held, at) : access.refused(snapshot, user, held, at, columns);
                if (decision.allowed() == allowed) {
                    event.decidedBy(decision).enforcedByGrantForge();
                }
            }
            events.add(event.build());
        }
        return events;
    }

    /** The client's address without its port. */
    static @Nullable String address(String client)
    {
        if (client.isEmpty()) {
            return null;
        }
        int colon = client.lastIndexOf(':');
        boolean port = colon > 0 && client.indexOf(':') == colon || client.startsWith("[") && colon > client.indexOf(']');
        String host = port ? client.substring(0, colon) : client;
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    /**
     * One access of a statement.
     *
     * @param type the access type
     * @param level the lowest level named: catalog, database, table or column
     * @param catalog the catalog
     * @param database the database, or {@code null} for the catalog
     * @param table the table, or {@code null}
     * @param columns the columns read or refused, none for the table as a whole
     */
    record Access(String type, String level, String catalog, @Nullable String database, @Nullable String table, List<String> columns)
    {
        Access
        {
            columns = List.copyOf(columns);
        }

        String resource()
        {
            if (database == null) {
                return catalog;
            }
            if (table == null) {
                return catalog + "." + database;
            }
            return catalog + "." + database + "." + table + ("column".equals(level) ? "." + columns.get(0) : "");
        }

        /** What the policies decide of it: each column read must be allowed, or the table when none is named. */
        AgentDecision decide(Snapshot snapshot, String user, Set<String> groups, Instant at)
        {
            AccessRequest.Builder base = request(user, groups, at).resource("catalog", catalog);
            if (database == null) {
                return snapshot.decide(base.build());
            }
            base.resource("database", database);
            if (table == null) {
                return snapshot.decide(base.build());
            }
            base.resource("table", table);
            if (columns.isEmpty()) {
                return snapshot.decide(base.below(Privileges.SELECT.equals(type)).build());
            }
            AgentDecision first = column(snapshot, user, groups, at, columns.get(0));
            for (String column : columns.subList(1, columns.size())) {
                if (!first.allowed()) {
                    return first;
                }
                AgentDecision decision = column(snapshot, user, groups, at, column);
                if (!decision.allowed()) {
                    return decision;
                }
            }
            return first;
        }

        /**
         * What the policies decide of an access Doris refused. Doris may name a table where the user lacks one of its
         * columns; the policies refuse it when they refuse the table or one of its columns.
         */
        AgentDecision refused(Snapshot snapshot, String user, Set<String> groups, Instant at, Function<TableRef, List<String>> columnsOf)
        {
            if (database == null || table == null || !columns.isEmpty() || !Privileges.SELECT.equals(type)) {
                return decide(snapshot, user, groups, at);
            }
            AgentDecision whole = snapshot.decide(request(user, groups, at).resource("catalog", catalog).resource("database", database)
                    .resource("table", table).build());
            if (!whole.allowed()) {
                return whole;
            }
            for (String column : columnsOf.apply(new TableRef(catalog, database, table))) {
                AgentDecision decision = column(snapshot, user, groups, at, column);
                if (!decision.allowed()) {
                    return decision;
                }
            }
            return whole;
        }

        private AgentDecision column(Snapshot snapshot, String user, Set<String> groups, Instant at, String column)
        {
            return snapshot.decide(request(user, groups, at).resource("catalog", catalog).resource("database", String.valueOf(database))
                    .resource("table", String.valueOf(table)).resource("column", column).build());
        }

        private AccessRequest.Builder request(String user, Set<String> groups, Instant at)
        {
            return AccessRequest.builder(user, type).groups(groups.toArray(new String[0])).time(at);
        }
    }

    /**
     * What a statement Doris ran touched, as Druid's Doris parser reads it: reading for each table read, loading for each
     * table written, updated, deleted from or truncated, dropping, altering and creating. Statements Druid does not read
     * are taken by their start; others, such as SHOW or GRANT, touch nothing the policies decide.
     *
     * @param statement the statement
     * @param catalog the session's catalog
     * @param database the session's database, or empty
     * @return the accesses
     */
    static List<Access> accesses(String statement, String catalog, String database)
    {
        Matcher alter = ALTER.matcher(statement);
        if (alter.find()) {
            Access table = table(Privileges.ALTER, alter.group(1), catalog, database, List.of());
            return table == null ? List.of() : List.of(table);
        }
        Matcher view = SHOW_VIEW.matcher(statement);
        if (view.find()) {
            Access table = table(Privileges.SHOW_VIEW, view.group(1), catalog, database, List.of());
            return table == null ? List.of() : List.of(table);
        }
        SQLStatement parsed;
        try {
            parsed = SQLUtils.parseSingleStatement(statement, DbType.doris);
        }
        catch (RuntimeException unreadable) {
            return List.of();
        }
        List<Access> accesses = new ArrayList<>();
        if (parsed instanceof SQLCreateDatabaseStatement create) {
            List<String> name = split(String.valueOf(create.getName()));
            accesses.add(new Access(Privileges.CREATE_DATABASE, "catalog", name.size() > 1 ? name.get(0) : catalog, null, null, List.of()));
            return accesses;
        }
        if (parsed instanceof SQLDropDatabaseStatement drop) {
            List<String> name = split(String.valueOf(drop.getDatabase()));
            accesses.add(new Access(Privileges.DROP_DATABASE, "database", name.size() > 1 ? name.get(0) : catalog, name.get(name.size() - 1), null,
                    List.of()));
            return accesses;
        }
        SchemaStatVisitor visitor = SQLUtils.createSchemaStatVisitor(DbType.doris);
        parsed.accept(visitor);
        Map<TableStat.Name, TableStat> tables = visitor.getTables();
        List<String> read = new ArrayList<>();
        for (TableStat.Column column : visitor.getColumns()) {
            read.add(column.getFullName());
        }
        boolean single = tables.size() == 1;
        for (Map.Entry<TableStat.Name, TableStat> entry : tables.entrySet()) {
            String name = entry.getKey().getName();
            TableStat stat = entry.getValue();
            if (stat.getCreateCount() > 0) {
                Access table = table(Privileges.CREATE, name, catalog, database, List.of());
                if (table != null) {
                    accesses.add(new Access(Privileges.CREATE, "database", table.catalog(), table.database(), null, List.of()));
                }
                continue;
            }
            add(accesses, stat.getDropCount() > 0 ? Privileges.DROP : null, name, catalog, database, List.of());
            add(accesses, stat.getInsertCount() + stat.getUpdateCount() + stat.getDeleteCount() > 0 ? Privileges.LOAD : null, name, catalog, database,
                    List.of());
            add(accesses, stat.getAlterCount() > 0 ? Privileges.ALTER : null, name, catalog, database, List.of());
            if (stat.getSelectCount() > 0) {
                add(accesses, Privileges.SELECT, name, catalog, database, columnsOf(name, read, single));
            }
        }
        return accesses;
    }

    private static void add(List<Access> accesses, @Nullable String type, String name, String catalog, String database, List<String> read)
    {
        if (type == null) {
            return;
        }
        Access table = table(type, name, catalog, database, read);
        if (table != null) {
            accesses.add(table);
        }
    }

    /**
     * What a refusal for a missing privilege names; a refusal that names no object is one access per table of the
     * statement. A failure for another reason is no access.
     *
     * @param error the error message
     * @param statement the statement
     * @param catalog the session's catalog
     * @param database the session's database, or empty
     * @return the accesses refused, none for a failure that is no refusal
     */
    static List<Access> denied(String error, String statement, String catalog, String database)
    {
        Matcher permission = PERMISSION.matcher(error);
        if (permission.find()) {
            String type = type(permission.group(1), "table");
            List<String> target = new ArrayList<>();
            Matcher part = Pattern.compile("\\[([^\\]]*)]").matcher(permission.group(2));
            while (part.find()) {
                target.add(part.group(1));
            }
            if (type != null && !target.isEmpty()) {
                return List.of(target(type, target));
            }
        }
        Matcher command = COMMAND.matcher(error);
        if (command.find()) {
            String type = type(command.group(1), "table");
            List<String> target = new ArrayList<>();
            for (String part : command.group(2).split("[:.]")) {
                target.add(part.strip());
            }
            if (target.size() == 2) {
                target.add(0, catalog);
            }
            if (type != null && target.size() == 3) {
                return List.of(target(type, target));
            }
        }
        Matcher need = NEED.matcher(error);
        if (need.find() || error.toLowerCase(Locale.ROOT).contains("access denied") || error.contains("Permission denied")) {
            String named = need.find(0) ? need.group(1) : null;
            List<Access> accesses = new ArrayList<>();
            for (Access access : accesses(statement, catalog, database)) {
                String type = named == null ? null : type(named, access.level());
                accesses.add(type == null ? access : new Access(type, access.level(), access.catalog(), access.database(), access.table(),
                        access.columns()));
            }
            return accesses;
        }
        return List.of();
    }

    /** The access type of the first privilege in a list such as {@code Admin_priv,Select_priv} that is no ADMIN. */
    private static @Nullable String type(String privileges, String level)
    {
        for (String privilege : privileges.split("[,\\s]+")) {
            if (privilege.isBlank() || privilege.toUpperCase(Locale.ROOT).startsWith("ADMIN")) {
                continue;
            }
            Set<String> types = Privileges.accessTypes(privilege, level);
            if (!types.isEmpty()) {
                return types.iterator().next();
            }
        }
        return null;
    }

    /** The access to what a refusal names: a catalog, database, table or column. */
    private static Access target(String type, List<String> target)
    {
        String catalog = target.get(0);
        return switch (target.size()) {
            case 1 -> new Access(type, "catalog", catalog, null, null, List.of());
            case 2 -> new Access(type, "database", catalog, target.get(1), null, List.of());
            case 3 -> new Access(type, "table", catalog, target.get(1), target.get(2), List.of());
            default -> new Access(type, "column", catalog, target.get(1), target.get(2), List.of(target.get(3)));
        };
    }

    /** A table named in a statement, with the session's catalog and database for the parts it leaves out. */
    private static @Nullable Access table(String type, String name, String catalog, String database, List<String> read)
    {
        List<String> parts = split(name);
        return switch (parts.size()) {
            case 1 -> database.isEmpty() ? null : new Access(type, "table", catalog, database, parts.get(0), read);
            case 2 -> new Access(type, "table", catalog, parts.get(0), parts.get(1), read);
            case 3 -> new Access(type, "table", parts.get(0), parts.get(1), parts.get(2), read);
            default -> null;
        };
    }

    /**
     * The columns of one table among those a statement read, named by Druid as {@code table.column}; a statement of one
     * table reads also the columns Druid could not tell the table of. A star reads the whole table.
     */
    private static List<String> columnsOf(String table, List<String> read, boolean single)
    {
        List<String> columns = new ArrayList<>();
        String start = table + ".";
        for (String column : read) {
            String name = column.startsWith(start) ? column.substring(start.length()) : single && column.startsWith("UNKNOWN.")
                    ? column.substring("UNKNOWN.".length()) : null;
            if (name == null) {
                continue;
            }
            if ("*".equals(name)) {
                return List.of();
            }
            if (!columns.contains(name)) {
                columns.add(name);
            }
        }
        return columns;
    }

    /** Splits a qualified name at its dots, backquotes taken off. */
    static List<String> split(String qualified)
    {
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        boolean quoted = false;
        int index = 0;
        while (index < qualified.length()) {
            char character = qualified.charAt(index);
            index++;
            if (character == '`') {
                if (quoted && index < qualified.length() && qualified.charAt(index) == '`') {
                    part.append('`');
                    index++;
                }
                else {
                    quoted = !quoted;
                }
            }
            else if (character == '.' && !quoted) {
                parts.add(part.toString());
                part.setLength(0);
            }
            else {
                part.append(character);
            }
        }
        parts.add(part.toString());
        return parts;
    }
}
