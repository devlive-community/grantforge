// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.EventFields;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
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
    private static final Snapshot POLICIES = new ElasticsearchSnapshots().allow(List.of("alice"), List.of("read"), "index", "logs-*", "field", "*")
            .deny(List.of("bob"), List.of("read"), "index", "hr", "field", "*").allow(List.of("@ops"), List.of("cluster_monitor"), "cluster", "*")
            .snapshot();

    @TempDir
    Path directory;

    /** A line of Elasticsearch's audit log. */
    private static String line(String outcome, String user, String action, String indices)
    {
        return "{\"type\":\"audit\", \"timestamp\":\"2026-10-26T07:24:34,764+0000\", \"node.id\":\"n1\", \"event.type\":\"transport\","
                + " \"event.action\":\"" + outcome + "\", \"user.name\":\"" + user + "\", \"user.realm\":\"native1\", \"origin.type\":\"rest\","
                + " \"origin.address\":\"10.0.0.7:51234\", \"url.path\":\"/logs-eu/_search\", \"request.method\":\"GET\", \"action\":\"" + action
                + "\", \"request.name\":\"SearchRequest\", \"indices\":" + indices + "}";
    }

    private static List<AccessEvent> events(String line, @Nullable Snapshot snapshot)
    {
        return AuditLog.events(line, "agent", snapshot, user -> "erin".equals(user) ? Set.of("ops") : Set.of(), INVENTORY);
    }

    @Test
    void reportsWhatElasticsearchAllowedAndRefusedWithWhoDecided()
    {
        AccessEvent allowed = events(line("access_granted", "alice", "indices:data/read/search", "[\"logs-eu\"]"), POLICIES).get(0);
        Map<String, @Nullable Object> fields = EventFields.of(allowed);
        assertThat(fields).containsEntry("user", "alice").containsEntry("resource", "logs-eu").containsEntry("accessType", "read")
                .containsEntry("outcome", "ALLOWED").containsEntry("enforcer", "GRANTFORGE").containsEntry("clientIp", "10.0.0.7")
                .containsEntry("request", "GET /logs-eu/_search").containsEntry("action", "indices:data/read/search");
        assertThat(fields).containsEntry("occurredAt", "2026-10-26T07:24:34.764Z");
        AccessEvent refused = events(line("access_denied", "bob", "indices:data/read/search", "[\"hr\"]"), POLICIES).get(0);
        assertThat(EventFields.of(refused)).containsEntry("outcome", "DENIED").containsEntry("enforcer", "GRANTFORGE");
        AccessEvent outside = events(line("access_granted", "bob", "indices:data/read/search", "[\"hr\"]"), POLICIES).get(0);
        assertThat(EventFields.of(outside)).as("a role outside GrantForge allowed what a policy denies").containsEntry("enforcer", "NATIVE");
        assertThat(EventFields.of(events(line("access_granted", "bob", "indices:data/read/search", "[\"hr\"]"), null).get(0)))
                .containsEntry("enforcer", "NATIVE");
    }

    @Test
    void namesTheClusterOrEachIndexAndDataStream()
    {
        assertThat(events(line("access_granted", "erin", "cluster:monitor/health", "[]"), POLICIES)).singleElement()
                .satisfies(event -> assertThat(EventFields.of(event)).containsEntry("resource", "search").containsEntry("resourceType", "cluster")
                        .containsEntry("enforcer", "GRANTFORGE"));
        assertThat(events(line("access_granted", "alice", "indices:data/read/search", "[\"logs-eu\", \".ds-app-000001\", \".security\"]"), POLICIES))
                .extracting(event -> EventFields.of(event).get("resource")).as("a backing index is its data stream; dot indices are left out")
                .containsExactly("logs-eu", "app");
        Inventory nameless = new Inventory("", new TreeSet<>(), Map.of(), target -> new TreeSet<>());
        assertThat(AuditLog.events(line("access_granted", "erin", "cluster:monitor/health", "[]"), "agent", POLICIES, user -> Set.of(), nameless))
                .isEmpty();
    }

    @Test
    void leavesOutWhatThePoliciesDoNotDecide()
    {
        assertThat(events(line("authentication_success", "alice", "indices:data/read/search", "[\"logs-eu\"]"), POLICIES)).isEmpty();
        assertThat(events(line("access_denied", "agent", "indices:data/read/search", "[\"logs-eu\"]"), POLICIES)).as("the agent itself").isEmpty();
        assertThat(events(line("access_denied", "_system", "indices:data/read/search", "[\"logs-eu\"]"), POLICIES)).as("an internal user").isEmpty();
        assertThat(events(line("access_denied", "", "indices:data/read/search", "[\"logs-eu\"]"), POLICIES)).isEmpty();
        assertThat(events(line("access_denied", "alice", "cluster:admin/settings/update", "[]"), POLICIES)).isEmpty();
        assertThat(events(line("access_denied", "alice", "indices:data/read/search", "[]"), POLICIES)).isEmpty();
        assertThat(events("not json", POLICIES)).isEmpty();
    }

    @Test
    void namesTheAccessTypeOfEachAction()
    {
        Map<String, String> types = Map.ofEntries(Map.entry("indices:data/read/get", "read"), Map.entry("indices:data/write/delete", "delete"),
                Map.entry("indices:data/write/index", "index"), Map.entry("indices:admin/auto_create", "create_index"),
                Map.entry("indices:admin/data_stream/create", "create_index"), Map.entry("indices:admin/delete", "delete_index"),
                Map.entry("indices:admin/data_stream/delete", "delete_index"), Map.entry("indices:admin/mappings/get", "view_index_metadata"),
                Map.entry("indices:admin/resolve/index", "view_index_metadata"), Map.entry("indices:monitor/stats", "monitor"),
                Map.entry("indices:admin/index_template/put", "manage_index_templates"), Map.entry("indices:admin/template/get", "manage_index_templates"),
                Map.entry("cluster:admin/component_template/put", "manage_index_templates"), Map.entry("indices:admin/mapping/put", "manage"),
                Map.entry("cluster:monitor/health", "cluster_monitor"), Map.entry("cluster:admin/snapshot/create", "create_snapshot"),
                Map.entry("cluster:admin/repository/put", "create_snapshot"), Map.entry("cluster:admin/slm/put", "manage_slm"),
                Map.entry("cluster:admin/ingest/pipeline/put", "manage_ingest_pipelines"), Map.entry("cluster:admin/ilm/put", "manage_ilm"));
        types.forEach((action, type) -> assertThat(AuditLog.accessType(action)).as(action).isEqualTo(type));
        assertThat(AuditLog.accessType("cluster:admin/settings/update")).isNull();
    }

    @Test
    void readsTimesAndAddressesAsElasticsearchWritesThem()
    {
        assertThat(AuditLog.instant("2026-10-26T07:24:34,764+0200")).isEqualTo(Instant.parse("2026-10-26T05:24:34.764Z"));
        assertThat(AuditLog.instant("2026-10-26T07:24:34.764Z")).isEqualTo(Instant.parse("2026-10-26T07:24:34.764Z"));
        assertThat(AuditLog.instant("2026-10-26T07:24:34.764+00:00")).isEqualTo(Instant.parse("2026-10-26T07:24:34.764Z"));
        assertThat(AuditLog.instant("soon")).isNotNull();
        assertThat(AuditLog.address("10.0.0.7:51234")).isEqualTo("10.0.0.7");
        assertThat(AuditLog.address("[::1]:51234")).isEqualTo("::1");
        assertThat(AuditLog.address("[::1")).isEqualTo("[::1");
        assertThat(AuditLog.address("::1")).isEqualTo("::1");
        assertThat(AuditLog.address("host")).isEqualTo("host");
        assertThat(AuditLog.address("")).isNull();
    }

    @Test
    void tailsEachFileFromWhereTheLastReadEndedAndFollowsRollovers() throws Exception
    {
        Path file = Files.writeString(directory.resolve("search_audit.json"), line("access_denied", "bob", "indices:data/read/search", "[\"hr\"]") + "\n");
        Path missing = directory.resolve("other_audit.json");
        List<AccessEvent> sent = new ArrayList<>();
        Path position = directory.resolve("cache").resolve("audit.position");
        AuditLog log = new AuditLog(new AgentConfig.AuditFiles(List.of(file, missing), Duration.ofSeconds(5), 2), "agent", position, () -> POLICIES,
                user -> Set.of(), () -> INVENTORY, sent::add);
        assertThat(log.poll()).as("a file read for the first time is read from its end").isZero();
        assertThat(sent).isEmpty();
        assertThat(Files.readString(position)).contains("\"offset\":" + Files.size(file));

        String denied = line("access_denied", "bob", "indices:data/read/search", "[\"hr\"]");
        Files.writeString(file, denied + "\n" + denied + "\n" + denied + "\n" + denied.substring(0, 20), StandardOpenOption.APPEND);
        assertThat(log.poll()).as("at most two lines each read").isEqualTo(2);
        assertThat(log.poll()).as("a line not yet ended waits").isEqualTo(1);
        assertThat(log.poll()).isZero();
        Files.writeString(file, denied.substring(20) + "\n", StandardOpenOption.APPEND);
        assertThat(log.poll()).isEqualTo(1);
        assertThat(sent).hasSize(4);

        Files.delete(file);
        Files.writeString(file, denied + "\n", StandardCharsets.UTF_8);
        assertThat(log.poll()).as("a file rolled over is read from its start").isEqualTo(1);
        assertThat(sent).hasSize(5);

        Files.writeString(position, "not json");
        assertThat(log.poll()).as("an unreadable position reads on from the present").isZero();
    }
}
