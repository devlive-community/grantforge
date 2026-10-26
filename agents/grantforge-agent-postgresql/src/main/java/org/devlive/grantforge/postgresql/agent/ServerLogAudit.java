// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports accesses from PostgreSQL's server log, which the server writes as JSON lines ({@code log_destination} with
 * {@code jsonlog}, and {@code logging_collector} on) and the agent reads through {@code pg_read_binary_file}, from where
 * the last read ended, which survives restarts; the first read starts at the present. What PostgreSQL allowed comes
 * from pgaudit's session log, one event per table a statement read or wrote, with the access type of what it did; what
 * PostgreSQL refused for a missing privilege is one denied event for what its error names. Each event says whether
 * GrantForge decided it, that is whether the policies decide as PostgreSQL did; an access PostgreSQL allows through a
 * privilege given outside GrantForge, such as a view's owner's, is the system's own decision.
 */
final class ServerLogAudit
{
    private static final Logger LOG = Logger.getLogger(ServerLogAudit.class.getName());

    /** The most bytes one read of the log asks for. */
    static final int CHUNK = 1024 * 1024;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectReader CSV = new CsvMapper().enable(CsvParser.Feature.WRAP_AS_ARRAY).readerFor(String[].class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z", Locale.ROOT);
    private static final DateTimeFormatter LOCAL_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);
    /** A log timestamp whose zone is a letter abbreviation, such as CEST, that java.time cannot parse by name. */
    private static final Pattern ZONE_ABBREVIATION = Pattern.compile("(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) ([A-Za-z]{2,5})");
    /** Offsets of the abbreviations PostgreSQL logs in common server time zones. */
    private static final Map<String, ZoneOffset> ABBREVIATED_OFFSETS = Map.ofEntries(
            Map.entry("UTC", ZoneOffset.UTC),
            Map.entry("GMT", ZoneOffset.UTC),
            Map.entry("BST", ZoneOffset.ofHours(1)),
            Map.entry("CET", ZoneOffset.ofHours(1)),
            Map.entry("CEST", ZoneOffset.ofHours(2)),
            Map.entry("EET", ZoneOffset.ofHours(2)),
            Map.entry("EEST", ZoneOffset.ofHours(3)),
            Map.entry("IST", ZoneOffset.ofHoursMinutes(5, 30)),
            Map.entry("JST", ZoneOffset.ofHours(9)),
            Map.entry("KST", ZoneOffset.ofHours(9)),
            Map.entry("AEST", ZoneOffset.ofHours(10)),
            Map.entry("EST", ZoneOffset.ofHours(-5)),
            Map.entry("EDT", ZoneOffset.ofHours(-4)),
            Map.entry("CST", ZoneOffset.ofHours(-6)),
            Map.entry("CDT", ZoneOffset.ofHours(-5)),
            Map.entry("MST", ZoneOffset.ofHours(-7)),
            Map.entry("MDT", ZoneOffset.ofHours(-6)),
            Map.entry("PST", ZoneOffset.ofHours(-8)),
            Map.entry("PDT", ZoneOffset.ofHours(-7)));

    /** A name in SQL, quoted or not, optionally with its schema. */
    private static final String NAME = "(?:\"(?:[^\"]|\"\")+\"|[A-Za-z_][A-Za-z0-9_$]*)";
    private static final String QUALIFIED = NAME + "(?:\\s*\\.\\s*" + NAME + ")?";
    private static final Pattern NAMES = Pattern.compile(QUALIFIED);
    private static final Pattern PART = Pattern.compile(NAME);
    private static final Pattern DENIED = Pattern.compile("permission denied for (table|view|materialized view|foreign table|sequence|schema"
            + "|database) \"?(.+?)\"?");
    private static final Pattern NO_TEMPORARY = Pattern.compile("permission denied to create temporary tables in database \"?(.+?)\"?");
    private static final Pattern TARGET = Pattern.compile("\\b(INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|MERGE\\s+INTO)\\s+(?:ONLY\\s+)?(" + QUALIFIED + ")",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRUNCATE = Pattern.compile("^\\s*TRUNCATE\\s+(?:TABLE\\s+)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern LISTED = Pattern.compile("\\G\\s*(?:ONLY\\s+)?(" + QUALIFIED + ")\\s*\\*?\\s*,?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATE = Pattern.compile("^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:(?:GLOBAL|LOCAL)\\s+)?(TEMP\\s+|TEMPORARY\\s+"
            + "|UNLOGGED\\s+)?(?:RECURSIVE\\s+)?(?:TABLE|VIEW|MATERIALIZED\\s+VIEW|SEQUENCE|FUNCTION|PROCEDURE|TYPE|DOMAIN)\\s+"
            + "(?:IF\\s+NOT\\s+EXISTS\\s+)?(" + QUALIFIED + ")", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRIGGER = Pattern.compile("^\\s*CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:CONSTRAINT\\s+)?TRIGGER\\b.*?\\bON\\s+(" + QUALIFIED
            + ")", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern COPY_IN = Pattern.compile("\\bFROM\\s+(?:STDIN|PROGRAM\\b|')", Pattern.CASE_INSENSITIVE);

    /** The kinds of objects pgaudit names that policies decide. */
    private static final Set<String> RELATIONS = Set.of("TABLE", "VIEW", "MATERIALIZED VIEW", "FOREIGN TABLE", "PARTITIONED TABLE");

    private final PostgreSql postgres;
    private final String home;
    private final AgentConfig.ServerLog settings;
    private final Path cursorFile;
    private final Supplier<@Nullable Snapshot> snapshots;
    private final Function<String, Set<String>> groups;
    private final Function<String, Inventory> inventories;
    private final Consumer<AccessEvent> sink;
    private final Set<String> reported = new HashSet<>();
    private final ReentrantLock reading = new ReentrantLock();
    private @Nullable Cursor cursor;

    /**
     * Where a read ended: the log file, how far into it, and when it was last written, to find the files after it once
     * it is gone.
     *
     * @param file the file's name in the log directory
     * @param offset the bytes read of it
     * @param modified when it was last written, in milliseconds since the epoch
     */
    record Cursor(String file, long offset, long modified)
    {
    }

    /** A log file as {@code pg_ls_logdir} shows it. */
    private record LogFile(String name, long size, long modified)
    {
    }

    /**
     * Reads the log.
     *
     * @param postgres the server
     * @param home the database the agent signs in to
     * @param settings how
     * @param cursorFile where the read position is kept
     * @param snapshots the policies, to say who decided
     * @param groups the roles each user is a member of, which policies name as groups
     * @param inventories the schemas, tables and columns of each database, as the last sync read them
     * @param sink where the events go
     */
    ServerLogAudit(PostgreSql postgres, String home, AgentConfig.ServerLog settings, Path cursorFile, Supplier<@Nullable Snapshot> snapshots,
            Function<String, Set<String>> groups, Function<String, Inventory> inventories, Consumer<AccessEvent> sink)
    {
        this.postgres = postgres;
        this.home = home;
        this.settings = settings;
        this.cursorFile = cursorFile;
        this.snapshots = snapshots;
        this.groups = groups;
        this.inventories = inventories;
        this.sink = sink;
    }

    /**
     * Reads what was logged since the last read, up to the most bytes one read takes.
     *
     * @return how many lines were read
     * @throws PostgreSqlFailure when the server fails a query or cannot be reached
     */
    int poll()
    {
        reading.lock();
        try {
            return pollOnce();
        }
        finally {
            reading.unlock();
        }
    }

    // A file and a cursor per file and chunk read are how reading moves on.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private int pollOnce()
    {
        Row setup = postgres.query(home, "SELECT current_user AS self, current_setting('log_destination') AS destination,"
                + " current_setting('logging_collector') AS collector, current_setting('log_directory') AS directory,"
                + " current_setting('shared_preload_libraries') AS preload").get(0);
        if (!setup.string("destination").contains("jsonlog") || !"on".equals(setup.string("collector"))) {
            once(Level.WARNING, "PostgreSQL writes no JSON server log (log_destination without jsonlog, or logging_collector off), so the"
                    + " GrantForge agent reports no accesses");
            return 0;
        }
        if (!setup.string("preload").contains("pgaudit")) {
            once(Level.INFO, "pgaudit is not loaded (shared_preload_libraries), so the GrantForge agent reports what PostgreSQL refused, not"
                    + " what it allowed");
        }
        List<LogFile> files = new ArrayList<>();
        for (Row row : postgres.query(home, "SELECT name, size, (extract(epoch FROM modification) * 1000)::bigint AS modified FROM pg_ls_logdir()"
                + " WHERE name LIKE '%.json' ORDER BY modification, name")) {
            files.add(new LogFile(row.string("name"), row.number("size"), row.number("modified")));
        }
        if (files.isEmpty()) {
            return 0;
        }
        Cursor at = cursor == null ? load() : cursor;
        if (at == null) {
            LogFile last = files.get(files.size() - 1);
            cursor = new Cursor(last.name(), last.size(), last.modified());
            save(cursor);
            return 0;
        }
        int start = -1;
        for (int index = 0; index < files.size(); index++) {
            if (files.get(index).name().equals(at.file())) {
                start = index;
            }
        }
        long offset = at.offset();
        if (start < 0) {
            // The file read last is gone: the files written since follow.
            offset = 0;
            start = files.size();
            for (int index = 0; index < files.size(); index++) {
                if (files.get(index).modified() >= at.modified()) {
                    start = index;
                    break;
                }
            }
        }
        else if (files.get(start).size() < offset) {
            // The file was truncated and written again, as log_truncate_on_rotation does.
            offset = 0;
        }
        String directory = setup.string("directory");
        Snapshot snapshot = snapshots.get();
        long budget = settings.maxBytes();
        int lines = 0;
        for (int index = start; index < files.size() && budget > 0; index++) {
            LogFile file = files.get(index);
            long from = index == start ? offset : 0;
            while (from < file.size() && budget > 0) {
                int length = (int) Math.min(Math.min(CHUNK, budget), file.size() - from);
                byte[] read = postgres.query(home, "SELECT pg_read_binary_file(?, ?, ?, true) AS bytes", directory + "/" + file.name(), from,
                        (long) length).get(0).bytes("bytes");
                int end = lastNewline(read);
                if (end < 0) {
                    if (read.length < CHUNK) {
                        // The last line is still being written.
                        break;
                    }
                    once(Level.WARNING, "A line of the PostgreSQL server log " + file.name() + " is longer than " + CHUNK + " bytes; it is skipped");
                    end = read.length - 1;
                }
                else {
                    lines += lines(new String(read, 0, end + 1, StandardCharsets.UTF_8), setup.string("self"), snapshot);
                }
                from += end + 1;
                budget -= end + 1;
                cursor = new Cursor(file.name(), from, file.modified());
                save(cursor);
            }
            if (from < file.size()) {
                break;
            }
            cursor = new Cursor(file.name(), from, file.modified());
        }
        return lines;
    }

    private static int lastNewline(byte[] bytes)
    {
        for (int index = bytes.length - 1; index >= 0; index--) {
            if (bytes[index] == '\n') {
                return index;
            }
        }
        return -1;
    }

    private int lines(String text, String self, @Nullable Snapshot snapshot)
    {
        int count = 0;
        for (String line : text.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            count++;
            try {
                for (AccessEvent event : events(JSON.readTree(line), self, snapshot, groups, inventories)) {
                    sink.accept(event);
                }
            }
            catch (JsonProcessingException unreadable) {
                once(Level.WARNING, "A line of the PostgreSQL server log is no JSON; such lines are skipped: " + unreadable.getOriginalMessage());
            }
        }
        return count;
    }

    /** Where the last read ended, or {@code null} when nothing was read before. */
    private @Nullable Cursor load()
    {
        try {
            if (Files.exists(cursorFile)) {
                String[] saved = Files.readString(cursorFile, StandardCharsets.UTF_8).strip().split("\t", 3);
                return new Cursor(saved[0], Long.parseLong(saved[1]), Long.parseLong(saved[2]));
            }
        }
        catch (IOException | RuntimeException unreadable) {
            LOG.log(Level.WARNING, "The server log position in " + cursorFile + " cannot be read; reading starts at the present", unreadable);
        }
        return null;
    }

    private void save(Cursor at)
    {
        try {
            Path directory = cursorFile.toAbsolutePath().getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            Path written = cursorFile.resolveSibling(cursorFile.getFileName() + ".tmp");
            Files.writeString(written, at.file() + "\t" + at.offset() + "\t" + at.modified(), StandardCharsets.UTF_8);
            try {
                Files.move(written, cursorFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException plain) {
                Files.move(written, cursorFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException unwritable) {
            LOG.log(Level.WARNING, "The server log position cannot be kept in " + cursorFile + "; a restart reads from the present", unwritable);
        }
    }

    private void once(Level level, String message)
    {
        if (reported.add(message)) {
            LOG.log(level, message);
        }
    }

    /**
     * Turns one entry of the log into events.
     *
     * @param entry the entry
     * @param self the agent's own user, whose statements are not reported
     * @param snapshot the policies, or {@code null} before the agent has any
     * @param groups the roles each user is a member of
     * @param inventories the schemas, tables and columns of each database
     * @return the events, none for an entry that tells of no access the policies decide
     */
    static List<AccessEvent> events(JsonNode entry, String self, @Nullable Snapshot snapshot, Function<String, Set<String>> groups,
            Function<String, Inventory> inventories)
    {
        String user = entry.path("user").asText("");
        String database = entry.path("dbname").asText("");
        if (user.isEmpty() || database.isEmpty() || user.equals(self) || !"client backend".equals(entry.path("backend_type").asText(""))) {
            return List.of();
        }
        String message = entry.path("message").asText("");
        String statement = entry.path("statement").asText("");
        String command = entry.path("ps").asText("");
        Inventory inventory = inventories.apply(database);
        List<Access> accesses;
        boolean allowed;
        if (message.startsWith("AUDIT: ")) {
            String[] fields = csv(message.substring("AUDIT: ".length()));
            if (fields.length < 8 || !"SESSION".equals(fields[0])) {
                return List.of();
            }
            allowed = true;
            statement = fields[7];
            command = fields[4];
            accesses = audited(fields[3], fields[4], fields[5], fields[6], fields[7], inventory);
        }
        else if (PostgreSqlFailure.INSUFFICIENT_PRIVILEGE.equals(entry.path("state_code").asText(""))) {
            allowed = false;
            accesses = refused(message, command, statement, database, inventory);
        }
        else {
            return List.of();
        }
        Instant at = instant(entry.path("timestamp").asText(""));
        String host = entry.path("remote_host").asText("");
        Set<String> held = groups.apply(user);
        List<AccessEvent> events = new ArrayList<>(accesses.size());
        for (Access access : accesses) {
            AccessEvent.Builder event = AccessEvent.builder(user, access.resource(database), access.type(), allowed).occurredAt(at)
                    .clientIp(host.isEmpty() || host.startsWith("[") ? null : host).resourceType(access.level())
                    .action(command.isEmpty() ? null : command).request(statement.isEmpty() ? null : statement);
            if (snapshot != null) {
                AgentDecision decision = allowed ? access.decide(snapshot, database, user, held, at)
                        : access.refused(snapshot, database, user, held, at, inventory);
                if (decision.allowed() == allowed) {
                    event.decidedBy(decision).enforcedByGrantForge();
                }
            }
            events.add(event.build());
        }
        return events;
    }

    /** The fields of pgaudit's CSV, none when it is no CSV. */
    static String[] csv(String text)
    {
        try (MappingIterator<String[]> rows = CSV.readValues(text)) {
            return rows.hasNext() ? rows.next() : new String[0];
        }
        catch (IOException | RuntimeException unreadable) {
            return new String[0];
        }
    }

    /** The moment of an entry, or the present when it cannot be read, such as in an unknown time zone. */
    static Instant instant(String timestamp)
    {
        try {
            return ZonedDateTime.parse(timestamp, TIMESTAMP).toInstant();
        }
        catch (DateTimeParseException unreadable) {
            Matcher abbreviated = ZONE_ABBREVIATION.matcher(timestamp);
            ZoneOffset offset = abbreviated.matches() ? ABBREVIATED_OFFSETS.get(abbreviated.group(2)) : null;
            if (offset != null) {
                return LocalDateTime.parse(abbreviated.group(1), LOCAL_TIMESTAMP).toInstant(offset);
            }
            return Instant.now();
        }
    }

    /**
     * One access of a statement, in its database.
     *
     * @param type the access type, or {@code connect} or {@code usage} for what reaching a database or schema needs
     * @param level the level named: database, schema or table
     * @param schema the schema, or {@code null} for the database
     * @param table the table, or {@code null}
     */
    record Access(String type, String level, @Nullable String schema, @Nullable String table)
    {
        static final String CONNECT = "connect";
        static final String USAGE = "usage";

        String resource(String database)
        {
            return database + (schema == null ? "" : "." + schema) + (table == null ? "" : "." + table);
        }

        /** What the policies decide of it; reading or writing a table is allowed when some of its columns may be. */
        AgentDecision decide(Snapshot snapshot, String database, String user, Set<String> groups, Instant at)
        {
            boolean reaching = CONNECT.equals(type) || USAGE.equals(type);
            AccessRequest.Builder request = AccessRequest.builder(user, reaching ? AccessRequest.ANY_ACCESS : type)
                    .groups(groups.toArray(new String[0])).time(at).resource("database", database);
            if (schema != null) {
                request.resource("schema", schema);
                if (table != null) {
                    request.resource("table", table).below(Privilege.COLUMN_TYPES.contains(type));
                }
            }
            return snapshot.decide(request.build());
        }

        /**
         * What the policies decide of an access PostgreSQL refused. PostgreSQL names the table where the user lacks a
         * column of it; the policies refuse it when they refuse the table or one of its columns.
         */
        AgentDecision refused(Snapshot snapshot, String database, String user, Set<String> groups, Instant at, Inventory inventory)
        {
            if (schema == null || table == null || !Privilege.COLUMN_TYPES.contains(type)) {
                return decide(snapshot, database, user, groups, at);
            }
            AccessRequest.Builder whole = AccessRequest.builder(user, type).groups(groups.toArray(new String[0])).time(at)
                    .resource("database", database).resource("schema", schema).resource("table", table);
            AgentDecision decided = snapshot.decide(whole.build());
            if (!decided.allowed()) {
                return decided;
            }
            for (String column : inventory.columns(new TableRef(schema, table))) {
                AgentDecision decision = snapshot.decide(AccessRequest.builder(user, type).groups(groups.toArray(new String[0])).time(at)
                        .resource("database", database).resource("schema", schema).resource("table", table).resource("column", column).build());
                if (!decision.allowed()) {
                    return decision;
                }
            }
            return decided;
        }
    }

    /** What one line of pgaudit's session log tells PostgreSQL allowed. */
    static List<Access> audited(String auditClass, String command, String objectType, String objectName, String statement, Inventory inventory)
    {
        switch (auditClass) {
            case "READ", "WRITE" -> {
                String type = "READ".equals(auditClass) ? Privilege.SELECT : written(command, statement);
                if (type == null) {
                    return List.of();
                }
                if (Privilege.TRUNCATE.equals(type) && objectName.isEmpty()) {
                    return truncated(statement, inventory);
                }
                if (!RELATIONS.contains(objectType)) {
                    return List.of();
                }
                List<String> name = parts(objectName);
                return name.size() == 2 && !system(name.get(0)) ? List.of(new Access(type, "table", name.get(0), name.get(1))) : List.of();
            }
            case "DDL" -> {
                return created(command, statement, inventory);
            }
            default -> {
                // Other classes, such as ROLE or MISC, touch nothing the policies decide.
                return List.of();
            }
        }
    }

    /** The access type of a write, by its command. */
    private static @Nullable String written(String command, String statement)
    {
        return switch (command) {
            case "INSERT" -> Privilege.INSERT;
            case "UPDATE", "SELECT" -> Privilege.UPDATE;
            case "DELETE" -> Privilege.DELETE;
            case "TRUNCATE TABLE" -> Privilege.TRUNCATE;
            case "COPY" -> COPY_IN.matcher(statement).find() ? Privilege.INSERT : Privilege.SELECT;
            default -> null;
        };
    }

    /** The tables a TRUNCATE names. */
    private static List<Access> truncated(String statement, Inventory inventory)
    {
        Matcher truncate = TRUNCATE.matcher(statement);
        if (!truncate.find()) {
            return List.of();
        }
        List<Access> accesses = new ArrayList<>();
        Matcher listed = LISTED.matcher(statement);
        listed.region(truncate.end(), statement.length());
        while (listed.find()) {
            TableRef table = resolve(parts(listed.group(1)), statement, inventory);
            if (table != null) {
                accesses.add(new Access(Privilege.TRUNCATE, "table", table.schema(), table.table()));
            }
            if (!listed.group().contains(",")) {
                break;
            }
        }
        return accesses;
    }

    /** What a DDL statement created, where the policies decide it. */
    private static List<Access> created(String command, String statement, Inventory inventory)
    {
        if ("CREATE SCHEMA".equals(command)) {
            return List.of(new Access(Privilege.CREATE_SCHEMA, "database", null, null));
        }
        Matcher trigger = TRIGGER.matcher(statement);
        if ("CREATE TRIGGER".equals(command) && trigger.find()) {
            TableRef table = resolve(parts(trigger.group(1)), statement, inventory);
            return table == null ? List.of() : List.of(new Access(Privilege.TRIGGER, "table", table.schema(), table.table()));
        }
        Matcher create = CREATE.matcher(statement);
        if (!command.startsWith("CREATE ") || !create.find()) {
            return List.of();
        }
        if (create.group(1) != null && create.group(1).toUpperCase(Locale.ROOT).startsWith("TEMP")) {
            return List.of(new Access(Privilege.TEMPORARY, "database", null, null));
        }
        List<String> name = parts(create.group(2));
        String schema = name.size() == 2 ? name.get(0) : "public";
        return system(schema) ? List.of() : List.of(new Access(Privilege.CREATE, "schema", schema, null));
    }

    /** What PostgreSQL's refusal for a missing privilege names. */
    static List<Access> refused(String message, String command, String statement, String database, Inventory inventory)
    {
        Matcher temporary = NO_TEMPORARY.matcher(message);
        if (temporary.matches()) {
            return List.of(new Access(Privilege.TEMPORARY, "database", null, null));
        }
        Matcher denied = DENIED.matcher(message);
        if (!denied.matches()) {
            return List.of();
        }
        String kind = denied.group(1);
        String name = denied.group(2);
        switch (kind) {
            case "database" -> {
                String type = "CREATE SCHEMA".equals(command) ? Privilege.CREATE_SCHEMA : Access.CONNECT;
                return name.equals(database) ? List.of(new Access(type, "database", null, null)) : List.of();
            }
            case "schema" -> {
                String type = command.startsWith("CREATE ") ? Privilege.CREATE : Access.USAGE;
                return system(name) ? List.of() : List.of(new Access(type, "schema", name, null));
            }
            case "sequence" -> {
                // Inserting into a serial column needs its sequence; the insert is what was refused.
                for (TableRef sequence : inventory.namedSequence(name)) {
                    TableRef table = inventory.filledBy(sequence);
                    if (table != null) {
                        return List.of(new Access(Privilege.INSERT, "table", table.schema(), table.table()));
                    }
                }
                return List.of();
            }
            default -> {
                TableRef table = resolve(List.of(name), statement, inventory);
                return table == null ? List.of() : List.of(new Access(type(command, statement, name), "table", table.schema(), table.table()));
            }
        }
    }

    /** The access type a refused statement asked of a table: what it does to its target, else reading it. */
    private static String type(String command, String statement, String table)
    {
        Matcher target = TARGET.matcher(statement);
        if (target.find()) {
            List<String> name = parts(target.group(2));
            if (name.get(name.size() - 1).equals(table)) {
                String verb = target.group(1).toUpperCase(Locale.ROOT);
                if (verb.startsWith("INSERT")) {
                    return Privilege.INSERT;
                }
                if (verb.startsWith("DELETE")) {
                    return Privilege.DELETE;
                }
                return Privilege.UPDATE;
            }
            return Privilege.SELECT;
        }
        return switch (command) {
            case "TRUNCATE TABLE" -> Privilege.TRUNCATE;
            case "CREATE TRIGGER" -> Privilege.TRIGGER;
            case "COPY" -> COPY_IN.matcher(statement).find() ? Privilege.INSERT : Privilege.SELECT;
            default -> Privilege.SELECT;
        };
    }

    /**
     * Finds the table a name stands for: with its schema as named, as the statement qualifies it, the one table of
     * the name, or the one in public.
     *
     * @param name the name, with or without its schema
     * @param statement the statement that names it
     * @param inventory the database's tables
     * @return the table, or {@code null} when it cannot be told
     */
    // A table per name the statement mentions.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    static @Nullable TableRef resolve(List<String> name, String statement, Inventory inventory)
    {
        if (name.size() == 2) {
            return system(name.get(0)) ? null : new TableRef(name.get(0), name.get(1));
        }
        String table = name.get(0);
        Matcher mentioned = NAMES.matcher(statement);
        while (mentioned.find()) {
            List<String> parts = parts(mentioned.group());
            if (parts.size() == 2 && parts.get(1).equals(table) && inventory.table(new TableRef(parts.get(0), table)) != null) {
                return new TableRef(parts.get(0), table);
            }
        }
        List<TableRef> named = inventory.named(table);
        if (named.size() == 1) {
            return named.get(0);
        }
        TableRef inPublic = new TableRef("public", table);
        return named.contains(inPublic) ? inPublic : null;
    }

    /** The parts of a qualified name as PostgreSQL reads them: quoted parts as they are, others in lower case. */
    static List<String> parts(String qualified)
    {
        List<String> parts = new ArrayList<>(2);
        Matcher part = PART.matcher(qualified);
        while (part.find()) {
            String text = part.group();
            parts.add(text.startsWith("\"") ? text.substring(1, text.length() - 1).replace("\"\"", "\"") : text.toLowerCase(Locale.ROOT));
        }
        return parts;
    }

    private static boolean system(String schema)
    {
        return "information_schema".equals(schema) || schema.startsWith("pg_");
    }
}
