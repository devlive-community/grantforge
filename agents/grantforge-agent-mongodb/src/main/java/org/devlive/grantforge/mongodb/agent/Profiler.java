// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.Document;
import org.devlive.grantforge.agent.sync.Notices;
import org.devlive.grantforge.agent.sync.PositionFile;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reports accesses from MongoDB's profiler, where a deployment without an audit log has one: the {@code system.profile}
 * collection of each database whose profiling an operator turned on. The agent never turns it on, as it costs the server,
 * and tells when it reads nothing. It reads the member it talks to, the primary of a replica set, behind the present so
 * that entries of the same moment are all there, from where the last read ended. The profiler records what ran, so it
 * tells of no refusals, and a {@code mongos} has none, so that a sharded cluster needs the audit log.
 */
// A Document is what the driver takes and answers with, not a choice among maps.
@SuppressWarnings("PMD.LooseCoupling")
final class Profiler
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger LOG = Logger.getLogger(Profiler.class.getName());

    private final MongoDb mongo;
    private final AgentConfig.Audit settings;
    private final PositionFile position;
    private final Supplier<@Nullable Inventory> inventory;
    private final AccessReporter reporter;
    private final Clock clock;
    private final Notices notices = new Notices(LOG);

    Profiler(MongoDb mongo, AgentConfig.Audit settings, Path position, Supplier<@Nullable Inventory> inventory, AccessReporter reporter, Clock clock)
    {
        this.mongo = mongo;
        this.settings = settings;
        this.position = new PositionFile(position);
        this.inventory = inventory;
        this.reporter = reporter;
        this.clock = clock;
    }

    /**
     * Reads the entries written since the last read.
     *
     * @return how many entries were read
     * @throws MongoFailure when the deployment cannot be reached
     */
    int poll()
    {
        Inventory known = inventory.get();
        if (known == null) {
            return 0;
        }
        if (known.sharded()) {
            notices.once(Level.WARNING, "A mongos has no profiler, so the GrantForge agent reports accesses of a sharded cluster only from the audit log; set "
                    + AgentConfig.PREFIX + "mongodb.audit.files");
            return 0;
        }
        Instant until = clock.instant().minus(settings.delay());
        Map<String, Instant> cursors = load();
        int read = 0;
        boolean moved = false;
        for (String database : known.databases()) {
            Instant after = cursors.get(database);
            if (after == null) {
                cursors.put(database, until);
                moved = true;
                continue;
            }
            List<Document> entries;
            try {
                entries = mongo.profile(database, after, until, settings.batch());
            }
            catch (MongoFailure failed) {
                if (failed.unreachable()) {
                    throw failed;
                }
                notices.once(Level.WARNING, "The GrantForge agent cannot read the profiler of database " + database + ": " + failed.getMessage());
                continue;
            }
            for (Document entry : entries) {
                report(entry);
                read++;
            }
            Instant reached = entries.size() >= settings.batch() && entries.get(entries.size() - 1).get("ts") instanceof Date last ? last.toInstant() : until;
            cursors.put(database, reached);
            moved = true;
        }
        if (moved) {
            save(cursors);
        }
        return read;
    }

    private void report(Document entry)
    {
        AccessReporter.Command command = command(entry);
        if (command != null) {
            reporter.report(command);
        }
    }

    /**
     * Reads one profiler entry.
     *
     * @param entry the entry
     * @return the command it tells of, or {@code null} for one that tells of none
     */
    static AccessReporter.@Nullable Command command(Document entry)
    {
        String operation = entry.getString("op");
        String namespace = entry.getString("ns");
        String user = entry.getString("user");
        Document arguments = entry.get("command", Document.class);
        if (operation == null || namespace == null || user == null || user.isEmpty()) {
            return null;
        }
        Document given = arguments == null ? new Document() : arguments;
        String name = switch (operation) {
            case "query" -> "find";
            case "insert" -> "insert";
            case "update" -> "update";
            case "remove" -> "delete";
            case "command" -> given.isEmpty() ? "" : given.keySet().iterator().next();
            default -> "";
        };
        int at = user.lastIndexOf('@');
        return new AccessReporter.Command(at < 0 ? user : user.substring(0, at), name, namespace, Accesses.of(name, namespace, given), true,
                entry.get("ts") instanceof Date ts ? ts.toInstant() : Instant.now(), entry.getString("client"));
    }

    private Map<String, Instant> load()
    {
        Map<String, Instant> cursors = new HashMap<>();
        try {
            String text = position.read();
            if (text != null) {
                JsonNode saved = JSON.readTree(text);
                saved.properties().forEach(entry -> cursors.put(entry.getKey(), Instant.ofEpochMilli(entry.getValue().asLong())));
            }
        }
        catch (IOException unreadable) {
            notices.once(Level.WARNING, "The GrantForge agent cannot read where it stopped reading the profiler, " + position.path()
                    + ", and reads on from the present: " + unreadable.getMessage());
        }
        return cursors;
    }

    private void save(Map<String, Instant> cursors)
    {
        ObjectNode saved = JSON.createObjectNode();
        cursors.forEach((database, at) -> saved.put(database, at.toEpochMilli()));
        try {
            position.write(saved.toString());
        }
        catch (IOException unwritable) {
            LOG.log(Level.WARNING, "The GrantForge agent cannot keep where it stopped reading the profiler, " + position.path(), unwritable);
        }
    }
}
