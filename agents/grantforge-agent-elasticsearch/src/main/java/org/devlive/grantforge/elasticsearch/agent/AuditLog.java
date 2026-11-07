// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.agent.sync.LogTail;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Reports accesses from Elasticsearch's audit log: the {@code access_granted} and {@code access_denied} events each
 * node writes to its own audit log file, one JSON object a line. The agent reads the files it can see, those of the node
 * it runs on or those a shared directory collects, from where the last read ended; a file read for the first time is
 * read from its end, and a file rolled over, so that another file now has its name, is read from its start. Each event
 * becomes an access event of every index it names, or of the cluster, with whether GrantForge's policies decide as
 * Elasticsearch did; the agent's own requests and Elasticsearch's internal users are left out.
 */
final class AuditLog
{
    /** The access types of the cluster as a whole. */
    static final Set<String> CLUSTER_TYPES = Set.of("cluster_monitor", "create_snapshot", "manage_slm", "manage_index_templates",
            "manage_ingest_pipelines", "manage_ilm");
    /** The most indices one audit event is reported for. */
    static final int MAX_INDICES = 20;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger LOG = Logger.getLogger(AuditLog.class.getName());
    /** Elasticsearch's audit time, such as {@code 2026-10-27T08:00:00,123+0000}. */
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[,SSS][.SSS]XX");

    private final String self;
    private final LogTail tail;
    private final Supplier<@Nullable Snapshot> snapshots;
    private final Function<String, Set<String>> groups;
    private final Supplier<Inventory> inventory;
    private final Consumer<AccessEvent> sink;

    AuditLog(AgentConfig.AuditFiles settings, String self, Path position, Supplier<@Nullable Snapshot> snapshots, Function<String, Set<String>> groups,
            Supplier<Inventory> inventory, Consumer<AccessEvent> sink)
    {
        this.self = self;
        this.tail = new LogTail(settings.files(), settings.maxLines(), position, LOG);
        this.snapshots = snapshots;
        this.groups = groups;
        this.inventory = inventory;
        this.sink = sink;
    }

    /**
     * Reads the lines written since the last read, at most as many of each file as the settings say.
     *
     * @return how many lines were read
     */
    int poll()
    {
        Snapshot snapshot = snapshots.get();
        Inventory known = inventory.get();
        return tail.poll(line -> {
            for (AccessEvent event : events(line, self, snapshot, groups, known)) {
                sink.accept(event);
            }
        });
    }

    /**
     * Turns one line of the audit log into events.
     *
     * @param line the line, a JSON object
     * @param self the agent's own user, whose requests are not reported
     * @param snapshot the policies, or {@code null} before the agent has any
     * @param groups the groups each user has
     * @param inventory the indices and data streams
     * @return the events, none for a line that tells of no access the policies decide
     */
    static List<AccessEvent> events(String line, String self, @Nullable Snapshot snapshot, Function<String, Set<String>> groups, Inventory inventory)
    {
        JsonNode event;
        try {
            event = JSON.readTree(line);
        }
        catch (IOException notJson) {
            return List.of();
        }
        String outcome = event.path("event.action").asText("");
        boolean allowed = "access_granted".equals(outcome);
        String user = event.path("user.name").asText("");
        String action = event.path("action").asText("");
        String type = accessType(action);
        if (!allowed && !"access_denied".equals(outcome) || user.isEmpty() || user.startsWith("_") || user.equals(self) || type == null) {
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
            for (JsonNode index : event.path("indices")) {
                String name = inventory.named(index.asText(""));
                if (!name.isEmpty() && !name.startsWith(".") && resources.size() < MAX_INDICES) {
                    resources.add(name);
                }
            }
        }
        Instant at = instant(event.path("timestamp").asText(event.path("@timestamp").asText("")));
        String address = address(event.path("origin.address").asText(""));
        String method = event.path("request.method").asText("");
        String path = event.path("url.path").asText("");
        String request = path.isEmpty() ? event.path("request.name").asText("") : (method + " " + path).strip();
        Set<String> held = groups.apply(user);
        List<AccessEvent> events = new ArrayList<>(resources.size());
        for (String resource : resources) {
            AccessEvent.Builder built = AccessEvent.builder(user, resource, type, allowed).occurredAt(at).clientIp(address)
                    .resourceType(cluster ? Planner.CLUSTER : Planner.INDEX).action(action).request(request.isEmpty() ? null : request);
            if (snapshot != null) {
                AgentDecision decision = snapshot.decide(AccessRequest.builder(user, type).groups(held.toArray(new String[0])).time(at)
                        .resource(cluster ? Planner.CLUSTER : Planner.INDEX, resource).below(Planner.READ.equals(type)).build());
                if (decision.allowed() == allowed) {
                    built.decidedBy(decision).enforcedByGrantForge();
                }
            }
            events.add(built.build());
        }
        return events;
    }

    /**
     * Names the access type an action of Elasticsearch is.
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
        if (action.startsWith("indices:admin/delete") || action.startsWith("indices:admin/data_stream/delete")) {
            return "delete_index";
        }
        if (action.startsWith("indices:admin/get") || action.startsWith("indices:admin/mappings/get") || action.startsWith("indices:admin/settings/get")
                || action.startsWith("indices:admin/aliases/get") || action.startsWith("indices:admin/resolve/")) {
            return "view_index_metadata";
        }
        if (action.startsWith("indices:monitor/")) {
            return "monitor";
        }
        if (action.startsWith("indices:admin/template/") || action.startsWith("indices:admin/index_template/")
                || action.startsWith("cluster:admin/component_template/")) {
            return "manage_index_templates";
        }
        if (action.startsWith("indices:admin/")) {
            return "manage";
        }
        if (action.startsWith("cluster:monitor/")) {
            return "cluster_monitor";
        }
        if (action.startsWith("cluster:admin/slm/")) {
            return "manage_slm";
        }
        if (action.startsWith("cluster:admin/snapshot/") || action.startsWith("cluster:admin/repository/")) {
            return "create_snapshot";
        }
        if (action.startsWith("cluster:admin/ingest/pipeline/")) {
            return "manage_ingest_pipelines";
        }
        if (action.startsWith("cluster:admin/ilm/")) {
            return "manage_ilm";
        }
        return null;
    }

    /** The client's address without its port, such as {@code 10.0.0.7} of {@code 10.0.0.7:51234}, or {@code null} for none. */
    static @Nullable String address(String origin)
    {
        if (origin.isEmpty()) {
            return null;
        }
        if (origin.startsWith("[")) {
            int end = origin.indexOf(']');
            return end < 0 ? origin : origin.substring(1, end);
        }
        int colon = origin.lastIndexOf(':');
        return colon > 0 && origin.indexOf(':') == colon ? origin.substring(0, colon) : origin;
    }

    /** The moment of an event, or the present when it cannot be read. */
    static Instant instant(String timestamp)
    {
        try {
            return OffsetDateTime.parse(timestamp, TIMESTAMP).toInstant();
        }
        catch (DateTimeParseException unreadable) {
            try {
                return OffsetDateTime.parse(timestamp).toInstant();
            }
            catch (DateTimeParseException neither) {
                return Instant.now();
            }
        }
    }
}
