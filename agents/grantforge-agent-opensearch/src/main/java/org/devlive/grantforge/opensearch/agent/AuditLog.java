// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.agent.sync.Notices;
import org.devlive.grantforge.agent.sync.PositionFile;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reports accesses from the security plugin's audit log, which it writes to indices of the cluster: what it refused,
 * as {@code MISSING_PRIVILEGES}, and what it allowed, as {@code GRANTED_PRIVILEGES} when the audit configuration logs
 * those. Each event names the user, the access type the action is, and each index or data stream it reached, or the
 * cluster, and says whether a GrantForge policy decided it. The documents are read in the order of their time from where
 * the last read ended, which a file of the cache directory keeps, so that a restart neither repeats nor loses them; the
 * first read starts at the present. The agent's own requests are not reported.
 */
final class AuditLog
{
    /** The access types of the cluster as a whole. */
    static final Set<String> CLUSTER_TYPES = Set.of("cluster_monitor", "manage_snapshots", "manage_templates", "manage_pipelines");
    /** The most indices one audit document is reported for. */
    static final int MAX_INDICES = 20;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger LOG = Logger.getLogger(AuditLog.class.getName());

    private final OpenSearch search;
    private final AgentConfig.AuditLogSettings settings;
    private final String self;
    private final PositionFile position;
    private final Supplier<@Nullable Snapshot> snapshots;
    private final Function<String, Set<String>> groups;
    private final Supplier<Inventory> inventory;
    private final Consumer<AccessEvent> sink;
    private final Clock clock;
    private final Notices notices = new Notices(LOG);

    AuditLog(OpenSearch search, AgentConfig.AuditLogSettings settings, String self, Path position, Supplier<@Nullable Snapshot> snapshots,
            Function<String, Set<String>> groups, Supplier<Inventory> inventory, Consumer<AccessEvent> sink, Clock clock)
    {
        this.search = search;
        this.settings = settings;
        this.self = self;
        this.position = new PositionFile(position);
        this.snapshots = snapshots;
        this.groups = groups;
        this.inventory = inventory;
        this.sink = sink;
        this.clock = clock;
    }

    /**
     * Where a read ended: the time of the last document read, and the documents of that very time already read.
     *
     * @param millis the time, in milliseconds since the epoch
     * @param ids the documents of that time
     */
    record Cursor(long millis, Set<String> ids)
    {
        /** Copies the documents. */
        Cursor
        {
            ids = Set.copyOf(ids);
        }
    }

    /**
     * Reads the audit documents written since the last read, at most as many as the settings say.
     *
     * @return how many documents were read
     * @throws OpenSearchFailure when the cluster refuses the search or cannot be reached
     */
    int poll()
    {
        Cursor cursor = load();
        JsonNode hits = search.send("POST", "/" + settings.index() + "/_search", Map.of("ignore_unavailable", "true", "allow_no_indices", "true"),
                query(cursor, settings.maxDocuments())).path("hits").path("hits");
        long millis = cursor.millis();
        Set<String> ids = new LinkedHashSet<>(cursor.ids());
        int read = 0;
        Snapshot snapshot = snapshots.get();
        Inventory known = inventory.get();
        for (JsonNode hit : hits) {
            String id = hit.path("_index").asText() + "/" + hit.path("_id").asText();
            long at = hit.path("sort").path(0).asLong(millis);
            if (at == cursor.millis() && cursor.ids().contains(id)) {
                continue;
            }
            if (at != millis) {
                millis = at;
                ids.clear();
            }
            ids.add(id);
            read++;
            for (AccessEvent event : events(hit.path("_source"), self, snapshot, groups, known)) {
                sink.accept(event);
            }
        }
        if (read > 0) {
            save(new Cursor(millis, ids));
        }
        return read;
    }

    /** The search of the documents at or after a cursor, oldest first. */
    static JsonNode query(Cursor cursor, int size)
    {
        ObjectNode query = JSON.createObjectNode();
        query.put("size", size);
        query.putArray("sort").addObject().putObject("@timestamp").put("order", "asc");
        ObjectNode range = query.putObject("query").putObject("range").putObject("@timestamp");
        range.put("gte", cursor.millis());
        range.put("format", "epoch_millis");
        return query;
    }

    private Cursor load()
    {
        try {
            String text = position.read();
            if (text != null) {
                JsonNode saved = JSON.readTree(text);
                Set<String> ids = new LinkedHashSet<>();
                saved.path("ids").forEach(id -> ids.add(id.asText()));
                return new Cursor(saved.path("millis").asLong(clock.millis()), ids);
            }
        }
        catch (IOException unreadable) {
            notices.once(Level.WARNING, "The GrantForge agent cannot read where it stopped reading the audit log, " + position.path()
                    + ", and reads on from the present: " + unreadable.getMessage());
        }
        Cursor present = new Cursor(clock.millis(), Set.of());
        save(present);
        return present;
    }

    private void save(Cursor cursor)
    {
        ObjectNode saved = JSON.createObjectNode();
        saved.put("millis", cursor.millis());
        ArrayNode ids = saved.putArray("ids");
        cursor.ids().forEach(ids::add);
        try {
            position.write(saved.toString());
        }
        catch (IOException unwritable) {
            LOG.log(Level.WARNING, "The GrantForge agent cannot keep where it stopped reading the audit log, " + position.path(), unwritable);
        }
    }

    /**
     * Turns one audit document into events.
     *
     * @param document the document
     * @param self the agent's own user, whose requests are not reported
     * @param snapshot the policies, or {@code null} before the agent has any
     * @param groups the backend roles each user has
     * @param inventory the indices and data streams
     * @return the events, none for a document that tells of no access the policies decide
     */
    static List<AccessEvent> events(JsonNode document, String self, @Nullable Snapshot snapshot, Function<String, Set<String>> groups,
            Inventory inventory)
    {
        String category = document.path("audit_category").asText("");
        boolean allowed = "GRANTED_PRIVILEGES".equals(category);
        String user = document.path("audit_request_effective_user").asText("");
        String privilege = document.path("audit_request_privilege").asText("");
        String type = accessType(privilege);
        if (!allowed && !"MISSING_PRIVILEGES".equals(category) || user.isEmpty() || user.equals(self) || type == null) {
            return List.of();
        }
        boolean cluster = CLUSTER_TYPES.contains(type);
        Set<String> resources = new LinkedHashSet<>();
        if (cluster) {
            if (!inventory.cluster().isEmpty()) {
                resources.add(inventory.cluster());
            }
        }
        else {
            JsonNode indices = document.path("audit_trace_resolved_indices");
            for (JsonNode index : indices.isArray() && !indices.isEmpty() ? indices : document.path("audit_trace_indices")) {
                String name = inventory.named(index.asText(""));
                if (!name.isEmpty() && !name.startsWith(".") && resources.size() < MAX_INDICES) {
                    resources.add(name);
                }
            }
        }
        Instant at = instant(document.path("@timestamp").asText(""));
        String address = document.path("audit_request_remote_address").asText("");
        String method = document.path("audit_rest_request_method").asText("");
        String path = document.path("audit_rest_request_path").asText("");
        String request = path.isEmpty() ? document.path("audit_transport_request_type").asText("") : (method + " " + path).strip();
        Set<String> held = groups.apply(user);
        List<AccessEvent> events = new ArrayList<>(resources.size());
        for (String resource : resources) {
            AccessEvent.Builder event = AccessEvent.builder(user, resource, type, allowed).occurredAt(at).clientIp(address.isEmpty() ? null : address)
                    .resourceType(cluster ? Planner.CLUSTER : Planner.INDEX).action(privilege).request(request.isEmpty() ? null : request);
            if (snapshot != null) {
                AgentDecision decision = snapshot.decide(AccessRequest.builder(user, type).groups(held.toArray(new String[0])).time(at)
                        .resource(cluster ? Planner.CLUSTER : Planner.INDEX, resource).below(Planner.READ.equals(type)).build());
                if (decision.allowed() == allowed) {
                    event.decidedBy(decision).enforcedByGrantForge();
                }
            }
            events.add(event.build());
        }
        return events;
    }

    /**
     * Names the access type an action of OpenSearch is.
     *
     * @param action the action, such as {@code indices:data/read/search}
     * @return the access type, or {@code null} for an action no access type decides
     */
    static @Nullable String accessType(String action)
    {
        if (action.startsWith("indices:data/read/")) {
            return Planner.READ;
        }
        if (action.startsWith("indices:data/write/delete")) {
            return "delete";
        }
        if (action.startsWith("indices:data/write/")) {
            return "index";
        }
        if (action.startsWith("indices:admin/create") || action.startsWith("indices:admin/auto_create")
                || action.startsWith("indices:admin/data_stream/create")) {
            return "create_index";
        }
        if (action.startsWith("indices:admin/aliases")) {
            return "manage_aliases";
        }
        if (action.startsWith("indices:monitor/")) {
            return "monitor";
        }
        if (action.startsWith("indices:admin/template/") || action.startsWith("indices:admin/index_template/")
                || action.startsWith("cluster:admin/component_template/")) {
            return "manage_templates";
        }
        if (action.startsWith("indices:admin/")) {
            return "manage";
        }
        if (action.startsWith("cluster:monitor/")) {
            return "cluster_monitor";
        }
        if (action.startsWith("cluster:admin/snapshot/") || action.startsWith("cluster:admin/repository/")) {
            return "manage_snapshots";
        }
        if (action.startsWith("cluster:admin/ingest/pipeline/")) {
            return "manage_pipelines";
        }
        return null;
    }

    /** The moment of a document, or the present when it cannot be read. */
    static Instant instant(String timestamp)
    {
        try {
            return OffsetDateTime.parse(timestamp).toInstant();
        }
        catch (DateTimeParseException unreadable) {
            return Instant.now();
        }
    }
}
