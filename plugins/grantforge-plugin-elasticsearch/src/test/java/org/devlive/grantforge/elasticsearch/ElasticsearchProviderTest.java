// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElasticsearchProviderTest
{
    private static final String ROOT = "{\"cluster_name\":\"search\",\"version\":{\"number\":\"9.5.5\",\"build_flavor\":\"default\"},"
            + "\"tagline\":\"You Know, for Search\"}";
    private static final String SIGNED_IN = "{\"username\":\"lookup\",\"roles\":[\"viewer\"]}";
    private static final String RESOLVED = "{\"indices\":[{\"name\":\"logs\"},{\"name\":\".ds-app-000001\",\"data_stream\":\"app\"}],"
            + "\"data_streams\":[{\"name\":\"app\"}]}";

    private final ElasticsearchProvider provider = new ElasticsearchProvider();

    private static ServiceConfig config(FakeElasticsearch node, String... values)
    {
        String[] all = new String[values.length + 2];
        all[0] = ElasticsearchSettings.HOSTS;
        all[1] = "http://127.0.0.1:" + node.port();
        System.arraycopy(values, 0, all, 2, values.length);
        return ElasticsearchSettingsTest.config(all);
    }

    private static String xpack(String type, String status, boolean security)
    {
        return "{\"license\":{\"type\":\"" + type + "\",\"mode\":\"" + type + "\",\"status\":\"" + status + "\"},"
                + "\"features\":{\"security\":{\"available\":true,\"enabled\":" + security + "}}}";
    }

    @Test
    void describesElasticsearchInItsOwnTerms()
    {
        ServiceTypeDefinition definition = provider.definition();
        assertThat(definition.name()).isEqualTo("elasticsearch");
        assertThat(definition.resources()).extracting(ResourceDefinition::name).containsExactly("index", "field", "cluster");
        ResourceDefinition field = definition.resources().get(1);
        assertThat(field.parent()).isEqualTo("index");
        assertThat(field.accessTypes()).containsExactly("read");
        assertThat(definition.resources().get(0).accessTypes()).containsExactlyInAnyOrder("index", "delete", "create_index", "delete_index",
                "view_index_metadata", "monitor", "manage");
        assertThat(definition.resources().get(2).accessTypes()).containsExactlyInAnyOrder("cluster_monitor", "create_snapshot", "manage_slm",
                "manage_index_templates", "manage_ingest_pipelines", "manage_ilm");
        assertThat(definition.resources()).allMatch(ResourceDefinition::caseSensitive).allMatch(ResourceDefinition::lookupSupported);
        AccessTypeDefinition manage = definition.accessTypes().stream().filter(type -> "manage".equals(type.name())).findFirst().orElseThrow();
        assertThat(manage.impliedGrants()).containsExactlyInAnyOrder("create_index", "delete_index", "view_index_metadata", "monitor");
        AccessTypeDefinition slm = definition.accessTypes().stream().filter(type -> "manage_slm".equals(type.name())).findFirst().orElseThrow();
        assertThat(slm.impliedGrants()).containsExactly("create_snapshot");
        assertThat(requireNonNull(definition.rowFilter()).resources()).containsExactly("index");
        assertThat(definition.dataMask()).as("Elasticsearch masks no values").isNull();
        assertThat(definition.configFields()).extracting(ConfigField::name).contains(ElasticsearchSettings.HOSTS, ElasticsearchSettings.API_KEY,
                ElasticsearchSettings.KEY_PASSPHRASE);
    }

    @Test
    void validatesTheSettings()
    {
        assertThat(provider.validateConfig(ElasticsearchSettingsTest.config())).isEmpty();
        assertThat(provider.validateConfig(ElasticsearchSettingsTest.config(ElasticsearchSettings.HOSTS, "ftp://x"))).hasSize(1);
    }

    @Test
    void theConnectionTestDescribesTheClusterAndItsLicense() throws Exception
    {
        try (FakeElasticsearch node = FakeElasticsearch.http().password("secret").answer("/", 200, ROOT).answer("/_xpack", 200, xpack("platinum", "active", true))
                .answer("/_security/_authenticate", 200, SIGNED_IN).answer("/_resolve/index/*", 200, RESOLVED)) {
            ConnectionResult result = provider.testConnection(config(node));
            assertThat(result.status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
            assertThat(result.message()).isEqualTo("Elasticsearch 9.5.5, cluster search, platinum license, signed in as lookup, 2 indices and data streams");
        }
        try (FakeElasticsearch node = FakeElasticsearch.http().password("secret").answer("/", 200, ROOT).answer("/_xpack", 200, xpack("basic", "active", true))
                .answer("/_security/_authenticate", 200, SIGNED_IN).answer("/_resolve/index/*", 200, "{\"indices\":[{\"name\":\"logs\"}]}")) {
            assertThat(provider.testConnection(config(node)).message()).isEqualTo("Elasticsearch 9.5.5, cluster search, basic license: row filters and"
                    + " field restrictions need Platinum or Enterprise, signed in as lookup, 1 index or data stream");
        }
        try (FakeElasticsearch node = FakeElasticsearch.http().password("secret").answer("/", 403, "{\"error\":{\"reason\":\"no privileges\"}}")
                .answer("/_xpack", 403, "{\"error\":{\"reason\":\"no privileges\"}}").answer("/_security/_authenticate", 200, SIGNED_IN)
                .answer("/_resolve/index/*", 200, "{\"indices\":[]}")) {
            assertThat(provider.testConnection(config(node)).message()).isEqualTo("Elasticsearch (the user may not read the cluster's release, which"
                    + " needs the monitor privilege), signed in as lookup, 0 indices and data streams");
        }
    }

    @Test
    void describesTheLicenseAsElasticsearchDecidesWhatItAllows() throws IOException
    {
        ObjectMapper json = new ObjectMapper();
        assertThat(ElasticsearchProvider.license(null)).isEmpty();
        assertThat(ElasticsearchProvider.license(json.readTree("{}"))).isEmpty();
        assertThat(ElasticsearchProvider.license(json.readTree(xpack("enterprise", "active", true)))).isEqualTo(", enterprise license");
        assertThat(ElasticsearchProvider.license(json.readTree(xpack("trial", "active", true)))).isEqualTo(", trial license");
        assertThat(ElasticsearchProvider.license(json.readTree(xpack("gold", "active", true))))
                .isEqualTo(", gold license: row filters and field restrictions need Platinum or Enterprise");
        assertThat(ElasticsearchProvider.license(json.readTree(xpack("platinum", "expired", true))))
                .isEqualTo(", platinum license expired: row filters and field restrictions need an active Platinum or Enterprise license");
    }

    @Test
    void theConnectionTestWantsElasticsearchWithSecurity() throws Exception
    {
        try (FakeElasticsearch node = FakeElasticsearch.http().password("secret").answer("/", 200,
                "{\"cluster_name\":\"x\",\"version\":{\"distribution\":\"opensearch\",\"number\":\"3.9.0\"}}")) {
            assertThat(provider.testConnection(config(node)).message())
                    .isEqualTo("the nodes at http://127.0.0.1:" + node.port() + " are an OpenSearch cluster, not Elasticsearch");
        }
        try (FakeElasticsearch node = FakeElasticsearch.http().answer("/", 200, ROOT).answer("/_xpack", 200, xpack("basic", "active", false))) {
            ConnectionResult result = provider.testConnection(config(node));
            assertThat(result.status()).isEqualTo(ConnectionResult.Status.FAILED);
            assertThat(result.message()).isEqualTo("Elasticsearch 9.5.5 runs without security, which GrantForge enforces policies with");
        }
        try (FakeElasticsearch node = FakeElasticsearch.http().password("secret").answer("/", 500, "{\"error\":\"down\"}")) {
            assertThat(provider.testConnection(config(node)).message()).endsWith("500 down");
        }
    }

    @Test
    void theConnectionTestReportsUnusableSettingsAndRefusals() throws Exception
    {
        assertThat(provider.testConnection(new ServiceConfig("search", Map.of())).message()).startsWith(ElasticsearchSettings.HOSTS + ": ");
        try (FakeElasticsearch node = FakeElasticsearch.http().password("other")) {
            assertThat(provider.testConnection(config(node)).message()).contains("401");
        }
    }

    @Test
    void looksUpThroughTheCluster() throws Exception
    {
        try (FakeElasticsearch node = FakeElasticsearch.http().password("secret").answer("/", 200, ROOT)) {
            assertThat(provider.lookup(new LookupRequest(config(node), ElasticsearchProvider.CLUSTER, "", Map.of(), 5))).containsExactly("search");
            assertThatThrownBy(() -> provider.lookup(new LookupRequest(config(node), "tenant", "", Map.of(), 5)))
                    .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
            assertThatThrownBy(() -> provider.lookup(new LookupRequest(new ServiceConfig("search", Map.of()), ElasticsearchProvider.INDEX, "", Map.of(), 5)))
                    .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
            assertThatThrownBy(() -> provider.lookup(new LookupRequest(config(node), ElasticsearchProvider.INDEX, "", Map.of(), 5)))
                    .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.NOT_FOUND));
        }
        assertThat(Set.of(ElasticsearchProvider.TYPE)).containsExactly("elasticsearch");
        assertThat(List.of(ElasticsearchProvider.READ, ElasticsearchProvider.WRITE)).containsExactly("read", "index");
    }
}
