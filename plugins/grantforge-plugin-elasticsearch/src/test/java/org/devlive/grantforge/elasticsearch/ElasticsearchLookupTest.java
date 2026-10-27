// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ElasticsearchLookupTest
{
    private static final String RESOLVED = """
            {"indices": [{"name": "logs-eu", "attributes": ["open"]}, {"name": "logs-us", "attributes": ["closed"]},
             {"name": ".ds-app-web-000001", "attributes": ["hidden", "open"], "data_stream": "app-web"}, {"name": ".kibana_1", "attributes": ["open"]}],
             "aliases": [{"name": "all-logs", "indices": ["logs-eu", "logs-us"]}],
             "data_streams": [{"name": "app-web", "backing_indices": [".ds-app-web-000001"], "timestamp_field": "@timestamp"}]}""";
    private static final String CAPABILITIES = """
            {"indices": ["logs-eu"], "fields": {"_id": {"_id": {"type": "_id"}}, "region": {"text": {"type": "text"}},
             "region.keyword": {"keyword": {"type": "keyword"}}, "address": {"object": {"type": "object"}},
             "address.city": {"keyword": {"type": "keyword"}}, "card": {"keyword": {"type": "keyword"}, "long": {"type": "long"}},
             "tags": {"nested": {"type": "nested"}}, "tags.name": {"keyword": {"type": "keyword"}}}}""";

    private static ElasticsearchLookup lookup(String resource, String typed, Map<String, List<String>> context)
    {
        return ElasticsearchLookup.of(resource, typed, context, 10);
    }

    private static List<String> run(ElasticsearchLookup lookup, FakeElasticsearch node)
    {
        try (ElasticsearchClient client = ElasticsearchClient.open(ElasticsearchSettings.of(ElasticsearchSettingsTest.config(ElasticsearchSettings.HOSTS,
                "http://127.0.0.1:" + node.port())))) {
            return lookup.run(client);
        }
    }

    @Test
    void knowsTheLevelsOfTheServiceType()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> lookup("tenant", "", Map.of())).withMessage("Elasticsearch services have no resource tenant");
        assertThat(lookup(ElasticsearchProvider.CLUSTER, " c ", Map.of()).typed()).isEqualTo("c");
    }

    @Test
    void narrowsFieldsByTheIndicesThePolicyNames()
    {
        assertThat(lookup(ElasticsearchProvider.FIELD, "", Map.of(ElasticsearchProvider.INDEX, List.of(" logs-* ", "app-?"))).indices())
                .as("Elasticsearch's expressions have no ?").containsExactly("logs-*", "app-*");
        assertThat(lookup(ElasticsearchProvider.FIELD, "", Map.of(ElasticsearchProvider.INDEX, List.of("logs", "*"))).indices()).isEmpty();
        assertThat(lookup(ElasticsearchProvider.FIELD, "", Map.of(ElasticsearchProvider.INDEX, List.of("a,b"))).indices()).isEmpty();
        assertThat(lookup(ElasticsearchProvider.FIELD, "", Map.of(ElasticsearchProvider.INDEX, List.of("-logs"))).indices())
                .as("a leading minus would exclude").isEmpty();
        assertThat(lookup(ElasticsearchProvider.FIELD, "", Map.of(ElasticsearchProvider.INDEX, List.of(""))).indices()).isEmpty();
        assertThat(lookup(ElasticsearchProvider.FIELD, "", Map.of(ElasticsearchProvider.INDEX, List.of())).indices()).isEmpty();
        assertThat(lookup(ElasticsearchProvider.FIELD, "", Map.of(ElasticsearchProvider.INDEX, Collections.nCopies(ElasticsearchLookup.MAX_INDICES + 1, "logs")))
                .indices()).isEmpty();
    }

    @Test
    void listsIndicesAndDataStreamsWithoutBackingAndDotIndices() throws Exception
    {
        try (FakeElasticsearch node = FakeElasticsearch.http().password("secret").answer("/_resolve/index/*", 200, RESOLVED)
                .answer("/_resolve/index/.k*", 200, RESOLVED).answer("/_resolve/index/lo*s*", 200, RESOLVED)) {
            assertThat(run(lookup(ElasticsearchProvider.INDEX, "", Map.of()), node)).containsExactly("app-web", "logs-eu", "logs-us");
            assertThat(node.sent().get(0).query()).isEqualTo("expand_wildcards=open,closed");
            assertThat(run(lookup(ElasticsearchProvider.INDEX, ".k", Map.of()), node)).containsExactly(".kibana_1", "app-web", "logs-eu", "logs-us");
            assertThat(node.sent().get(1).query()).isEqualTo("expand_wildcards=all");
            assertThat(run(lookup(ElasticsearchProvider.INDEX, "lo?s", Map.of()), node)).hasSize(3);
            int asked = node.sent().size();
            assertThat(run(lookup(ElasticsearchProvider.INDEX, "a/b", Map.of()), node)).isEmpty();
            assertThat(run(lookup(ElasticsearchProvider.INDEX, "_x", Map.of()), node)).isEmpty();
            assertThat(node.sent()).as("names no index may have are not asked for").hasSize(asked);
        }
    }

    @Test
    void listsTheFieldsOfDocumentsOnly() throws Exception
    {
        try (FakeElasticsearch node = FakeElasticsearch.http().password("secret").answer("/logs-eu,app-*/_field_caps", 200, CAPABILITIES)
                .answer("/*,-.*/_field_caps", 200, CAPABILITIES)) {
            assertThat(run(lookup(ElasticsearchProvider.FIELD, "", Map.of(ElasticsearchProvider.INDEX, List.of("logs-eu", "app-?"))), node))
                    .containsExactly("address.city", "card", "region", "tags.name");
            assertThat(node.sent().get(0).query()).contains("fields=*").contains("ignore_unavailable=true").contains("allow_no_indices=true");
            assertThat(run(lookup(ElasticsearchProvider.FIELD, "re", Map.of()), node)).containsExactly("region");
        }
    }

    @Test
    void listsTheClusterByItsName() throws Exception
    {
        try (FakeElasticsearch node = FakeElasticsearch.http().password("secret").answer("/", 200, "{\"cluster_name\":\"search\"}")) {
            assertThat(run(lookup(ElasticsearchProvider.CLUSTER, "se", Map.of()), node)).containsExactly("search");
            assertThat(run(lookup(ElasticsearchProvider.CLUSTER, "x", Map.of()), node)).isEmpty();
        }
        try (FakeElasticsearch node = FakeElasticsearch.http().password("secret").answer("/", 200, "{}")) {
            assertThat(run(lookup(ElasticsearchProvider.CLUSTER, "", Map.of()), node)).isEmpty();
        }
    }

    @Test
    void stopsAtTheLimit() throws Exception
    {
        try (FakeElasticsearch node = FakeElasticsearch.http().password("secret").answer("/_resolve/index/*", 200, RESOLVED)) {
            assertThat(run(ElasticsearchLookup.of(ElasticsearchProvider.INDEX, "", Map.of(), 2), node)).containsExactly("app-web", "logs-eu");
        }
    }

    @Test
    void tellsMultiFieldsFromTheFieldsOfObjects()
    {
        Map<String, Set<String>> types = Map.of("name", Set.of("text"), "address", Set.of("object"), "tags", Set.of("nested"));
        assertThat(ElasticsearchLookup.multiField("name.keyword", types)).isTrue();
        assertThat(ElasticsearchLookup.multiField("address.city", types)).isFalse();
        assertThat(ElasticsearchLookup.multiField("tags.name", types)).isFalse();
        assertThat(ElasticsearchLookup.multiField("orphan.leaf", types)).isFalse();
        assertThat(ElasticsearchLookup.multiField("plain", types)).isFalse();
        assertThat(ElasticsearchLookup.multiField(".hidden", types)).isFalse();
    }

    @Test
    void encodesPathSegments()
    {
        assertThat(ElasticsearchLookup.path("logs-*", "_field_caps")).as("Elasticsearch decodes what is escaped").isEqualTo("/logs-%2A/_field_caps");
        assertThat(ElasticsearchLookup.path("a b", "c/d")).isEqualTo("/a%20b/c%2Fd");
    }
}
