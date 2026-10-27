// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyncTest
{
    @TempDir
    Path directory;

    private final AtomicReference<@Nullable Snapshot> snapshot = new AtomicReference<>();

    private Sync sync(FakeElasticsearch search) throws Exception
    {
        return new Sync(search, AgentConfig.parse(AgentConfigTest.minimal(directory), "1"), snapshot::get,
                Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void changesNothingWithoutASnapshot() throws Exception
    {
        FakeElasticsearch search = ServerStateTest.cluster();
        Sync sync = sync(search);
        assertThat(sync.run()).isNull();
        assertThat(sync.run()).isNull();
        assertThat(search.sent()).isEmpty();
        assertThat(sync.inventory().names()).isEmpty();
        assertThat(sync.groupsOf("alice")).isEmpty();
    }

    @Test
    void keepsTheRolesThePoliciesGive() throws Exception
    {
        FakeElasticsearch search = ServerStateTest.cluster();
        Sync sync = sync(search);
        snapshot.set(new ElasticsearchSnapshots().allow(List.of("bob"), List.of("read"), "index", "hr", "field", "*")
                .filter(List.of("bob"), "{\"term\": {\"x\": 1}}", "hr").snapshot());
        Reconciler.Outcome outcome = requireNonNull(sync.run());
        assertThat(outcome.created()).isEqualTo(1);
        assertThat(search.roles.path("grantforge_bob").toString()).contains("\"query\":\"{\\\"term\\\": {\\\"x\\\": 1}}\"");
        assertThat(sync.groupsOf("alice")).containsExactly("analysts");
        assertThat(sync.groupsOf("nobody")).isEmpty();
        assertThat(sync.inventory().names()).contains("hr");
        assertThat(sync.role("bob")).isEqualTo("grantforge_bob");
        assertThat(requireNonNull(sync.run()).changed()).isFalse();
    }

    @Test
    void belowPlatinumReadsNeitherSomeFieldsNorSomeDocuments() throws Exception
    {
        FakeElasticsearch search = ServerStateTest.cluster().license("basic", "active");
        Map<String, String> values = AgentConfigTest.minimal(directory);
        values.put("grantforge.audit.log.files", directory.resolve("audit.json").toString());
        Sync sync = new Sync(search, AgentConfig.parse(values, "1"), snapshot::get, Clock.systemUTC());
        snapshot.set(new ElasticsearchSnapshots().allow(List.of("bob"), List.of("read", "index"), "index", "hr", "field", "*")
                .filter(List.of("bob"), "{\"term\": {\"x\": 1}}", "hr").snapshot());
        sync.run();
        assertThat(search.roles.path("grantforge_bob").path("indices").path(0).path("privileges").toString()).isEqualTo("[\"index\"]");
        assertThat(search.sent("POST", "/*,-.*/_validate/query")).as("no query is checked that will not be granted").isEmpty();
    }

    @Test
    void grantsNothingForAServiceNotInUseOrOfAnotherType() throws Exception
    {
        FakeElasticsearch search = ServerStateTest.cluster();
        Sync sync = sync(search);
        snapshot.set(new ElasticsearchSnapshots().allow(List.of("bob"), List.of("read"), "index", "hr", "field", "*").disabled().snapshot());
        assertThat(requireNonNull(sync.run()).dropped()).isEqualTo(2);
        snapshot.set(new ElasticsearchSnapshots().allow(List.of("bob"), List.of("read"), "index", "hr", "field", "*").serviceType("hive").snapshot());
        assertThat(requireNonNull(sync.run()).created()).isZero();
        assertThat(search.roles.has("grantforge_bob")).isFalse();
    }

    @Test
    void asksElasticsearchWhetherItWouldRunAQuery() throws Exception
    {
        FakeElasticsearch search = new FakeElasticsearch();
        Sync sync = sync(search);
        assertThat(sync.refusal("{\"match_all\": {}}")).isNull();
        assertThat(search.sent().get(0).parameters()).containsEntry("explain", "true");
        assertThat(String.valueOf(search.sent().get(0).body())).isEqualTo("{\"query\":{\"match_all\":{}}}");
        assertThat(sync.refusal("{\"bogus\": {}}")).isEqualTo("unknown query [bogus]");
        assertThat(sync.refusal("not json")).isEqualTo("it is no JSON");
        assertThat(sync.refusal("[1]")).isEqualTo("it is no JSON object of one query");
        assertThat(sync.refusal("{\"a\": {}, \"b\": {}}")).isEqualTo("it is no JSON object of one query");
        search.answer("POST /*,-.*/_validate/query", FakeElasticsearch.json("{\"valid\": false, \"explanations\": [{\"error\": \"why\"}, {\"error\": \"x\"}]}"));
        assertThat(sync.refusal("{\"x\": {}}")).isEqualTo("why");
        search.answer("POST /*,-.*/_validate/query", FakeElasticsearch.json("{\"valid\": false}"));
        assertThat(sync.refusal("{\"x\": {}}")).isEqualTo("Elasticsearch finds it invalid");
        search.fail("POST /*,-.*/_validate/query", 400);
        assertThat(sync.refusal("{\"x\": {}}")).contains("400 refused");
        search.heal("POST /*,-.*/_validate/query").fail("POST /*,-.*/_validate/query", 503);
        assertThatThrownBy(() -> sync.refusal("{\"x\": {}}")).isInstanceOf(ElasticsearchFailure.class);
    }
}
