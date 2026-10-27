// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.EventFields;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogTest
{
    private static final Inventory INVENTORY = new Inventory("search", new TreeSet<>(Set.of("hr", "logs-eu", "app")), Map.of(".ds-app-000001", "app"),
            target -> new TreeSet<>());
    private static final Snapshot POLICIES = new OpenSearchSnapshots().allow(List.of("alice"), List.of("read"), "index", "logs-*", "field", "*")
            .deny(List.of("bob"), List.of("read"), "index", "hr", "field", "*").allow(List.of("@ops"), List.of("cluster_monitor"), "cluster", "*")
            .snapshot();

    @TempDir
    Path directory;

    private static JsonNode document(String category, String user, String privilege, String indices)
    {
        return FakeOpenSearch.json("{\"audit_category\": \"" + category + "\", \"audit_request_effective_user\": \"" + user + "\","
                + " \"audit_request_privilege\": \"" + privilege + "\", \"audit_trace_resolved_indices\": " + indices + ","
                + " \"@timestamp\": \"2026-10-26T07:24:34.764+00:00\", \"audit_request_remote_address\": \"10.0.0.7\","
                + " \"audit_rest_request_method\": \"GET\", \"audit_rest_request_path\": \"/logs-eu/_search\"}");
    }

    private static List<AccessEvent> events(JsonNode document, @Nullable Snapshot snapshot)
    {
        return AuditLog.events(document, "agent", snapshot, user -> "erin".equals(user) ? Set.of("ops") : Set.of(), INVENTORY);
    }

    @Test
    void reportsWhatTheSecurityPluginAllowedAndRefusedWithWhoDecided()
    {
        AccessEvent allowed = events(document("GRANTED_PRIVILEGES", "alice", "indices:data/read/search", "[\"logs-eu\"]"), POLICIES).get(0);
        Map<String, @Nullable Object> fields = EventFields.of(allowed);
        assertThat(fields).containsEntry("user", "alice").containsEntry("resource", "logs-eu").containsEntry("accessType", "read")
                .containsEntry("outcome", "ALLOWED").containsEntry("enforcer", "GRANTFORGE").containsEntry("clientIp", "10.0.0.7")
                .containsEntry("action", "indices:data/read/search").containsEntry("request", "GET /logs-eu/_search").containsEntry("resourceType", "index");
        Map<String, @Nullable Object> refused = EventFields.of(events(document("MISSING_PRIVILEGES", "bob", "indices:data/read/search", "[\"hr\"]"),
                POLICIES).get(0));
        assertThat(refused).containsEntry("outcome", "DENIED").containsEntry("enforcer", "GRANTFORGE").containsEntry("policyId", "2");
        Map<String, @Nullable Object> outside = EventFields.of(events(document("GRANTED_PRIVILEGES", "bob", "indices:data/read/search", "[\"hr\"]"),
                POLICIES).get(0));
        assertThat(outside.get("enforcer")).as("a role outside GrantForge allowed it").isNotEqualTo("GRANTFORGE");
        assertThat(EventFields.of(events(document("GRANTED_PRIVILEGES", "alice", "indices:data/read/search", "[\"logs-eu\"]"), null).get(0))
                .get("enforcer")).isNotEqualTo("GRANTFORGE");
    }

    @Test
    void namesTheClusterOrEachIndexAndDataStream()
    {
        List<AccessEvent> cluster = events(document("GRANTED_PRIVILEGES", "erin", "cluster:monitor/health", "[]"), POLICIES);
        assertThat(cluster).singleElement().satisfies(event -> assertThat(EventFields.of(event)).containsEntry("resource", "search")
                .containsEntry("resourceType", "cluster").containsEntry("enforcer", "GRANTFORGE"));
        assertThat(AuditLog.events(document("GRANTED_PRIVILEGES", "erin", "cluster:monitor/health", "[]"), "agent", null, user -> Set.of(),
                Inventory.EMPTY)).isEmpty();
        List<AccessEvent> indices = events(document("MISSING_PRIVILEGES", "alice", "indices:data/write/bulk[s]",
                "[\".ds-app-000001\", \".kibana\", \"logs-eu\", \"logs-eu\"]"), POLICIES);
        assertThat(indices).extracting(event -> EventFields.of(event).get("resource")).containsExactly("app", "logs-eu");
        JsonNode traced = FakeOpenSearch.json("{\"audit_category\": \"MISSING_PRIVILEGES\", \"audit_request_effective_user\": \"alice\","
                + " \"audit_request_privilege\": \"indices:admin/create\", \"audit_trace_indices\": [\"new\"], \"@timestamp\": \"later\","
                + " \"audit_transport_request_type\": \"CreateIndexRequest\"}");
        Map<String, @Nullable Object> created = EventFields.of(events(traced, POLICIES).get(0));
        assertThat(created).containsEntry("resource", "new").containsEntry("accessType", "create_index").containsEntry("request", "CreateIndexRequest");
        StringBuilder many = new StringBuilder("[");
        for (int index = 0; index < 30; index++) {
            many.append(index == 0 ? "" : ",").append("\"i").append(index).append('"');
        }
        assertThat(events(document("MISSING_PRIVILEGES", "alice", "indices:data/read/search", many + "]"), POLICIES)).hasSize(AuditLog.MAX_INDICES);
    }

    @Test
    void leavesOutWhatThePoliciesDoNotDecide()
    {
        assertThat(events(document("FAILED_LOGIN", "alice", "indices:data/read/search", "[\"logs-eu\"]"), POLICIES)).isEmpty();
        assertThat(events(document("MISSING_PRIVILEGES", "agent", "indices:data/read/search", "[\"logs-eu\"]"), POLICIES)).isEmpty();
        assertThat(events(document("MISSING_PRIVILEGES", "", "indices:data/read/search", "[\"logs-eu\"]"), POLICIES)).isEmpty();
        assertThat(events(document("MISSING_PRIVILEGES", "alice", "cluster:admin/opendistro/ism", "[\"logs-eu\"]"), POLICIES)).isEmpty();
        assertThat(events(document("MISSING_PRIVILEGES", "alice", "indices:data/read/search", "[]"), POLICIES)).isEmpty();
    }

    @Test
    void namesTheAccessTypeOfEachAction()
    {
        Map<String, String> types = Map.ofEntries(Map.entry("indices:data/read/get", "read"), Map.entry("indices:data/write/delete", "delete"),
                Map.entry("indices:data/write/index", "index"), Map.entry("indices:admin/auto_create", "create_index"),
                Map.entry("indices:admin/data_stream/create", "create_index"), Map.entry("indices:admin/aliases", "manage_aliases"),
                Map.entry("indices:monitor/stats", "monitor"), Map.entry("indices:admin/index_template/put", "manage_templates"),
                Map.entry("indices:admin/template/get", "manage_templates"), Map.entry("cluster:admin/component_template/put", "manage_templates"),
                Map.entry("indices:admin/mapping/put", "manage"), Map.entry("cluster:monitor/health", "cluster_monitor"),
                Map.entry("cluster:admin/snapshot/create", "manage_snapshots"), Map.entry("cluster:admin/repository/put", "manage_snapshots"),
                Map.entry("cluster:admin/ingest/pipeline/put", "manage_pipelines"));
        types.forEach((action, type) -> assertThat(AuditLog.accessType(action)).as(action).isEqualTo(type));
        assertThat(AuditLog.accessType("cluster:admin/settings/update")).isNull();
        assertThat(AuditLog.instant("2026-10-26T07:24:34.764+00:00")).isEqualTo(Instant.parse("2026-10-26T07:24:34.764Z"));
        assertThat(AuditLog.instant("soon")).isNotNull();
    }

    @Test
    void readsOnFromWhereTheLastReadEnded() throws Exception
    {
        FakeOpenSearch search = new FakeOpenSearch();
        String hits = "{\"hits\": {\"hits\": [{\"_index\": \"security-auditlog-1\", \"_id\": \"a\", \"sort\": [1000], \"_source\": %s},"
                + " {\"_index\": \"security-auditlog-1\", \"_id\": \"b\", \"sort\": [2000], \"_source\": %s}]}}";
        String source = document("MISSING_PRIVILEGES", "bob", "indices:data/read/search", "[\"hr\"]").toString();
        search.answer("POST /security-auditlog-*/_search", FakeOpenSearch.json(hits.formatted(source, source)));
        List<AccessEvent> sent = new ArrayList<>();
        Path position = directory.resolve("cache").resolve("audit.position");
        AuditLog log = new AuditLog(search, new AgentConfig.AuditLogSettings("security-auditlog-*", Duration.ofSeconds(5), 100), "agent", position,
                () -> POLICIES, user -> Set.of(), () -> INVENTORY, sent::add, Clock.fixed(Instant.ofEpochMilli(500), ZoneOffset.UTC));
        assertThat(log.poll()).isEqualTo(2);
        assertThat(sent).hasSize(2);
        JsonNode first = search.sent().get(0).body();
        assertThat(String.valueOf(first)).contains("\"gte\":500", "epoch_millis", "\"size\":100");
        assertThat(Files.readString(position)).isEqualTo("{\"millis\":2000,\"ids\":[\"security-auditlog-1/b\"]}");

        assertThat(log.poll()).as("b is at the cursor and was read").isEqualTo(1);
        assertThat(String.valueOf(search.sent().get(1).body())).contains("\"gte\":2000");
        Files.writeString(position, "not json");
        search.answer("POST /security-auditlog-*/_search", FakeOpenSearch.json("{\"hits\": {\"hits\": []}}"));
        assertThat(log.poll()).isZero();
        assertThat(Files.readString(position)).as("an unreadable cursor starts at the present").contains("\"millis\":500");
    }

    @Test
    void searchesTheDocumentsAtOrAfterTheCursorOldestFirst()
    {
        assertThat(AuditLog.query(new AuditLog.Cursor(42, Set.of("x")), 7).toString())
                .isEqualTo("{\"size\":7,\"sort\":[{\"@timestamp\":{\"order\":\"asc\"}}],\"query\":{\"range\":{\"@timestamp\":{\"gte\":42,"
                        + "\"format\":\"epoch_millis\"}}}}");
    }
}
