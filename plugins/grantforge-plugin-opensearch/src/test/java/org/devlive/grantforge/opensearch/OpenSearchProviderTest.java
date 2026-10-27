// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.MaskTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenSearchProviderTest
{
    private static final String ROOT = "{\"cluster_name\":\"search\",\"version\":{\"distribution\":\"opensearch\",\"number\":\"3.9.0\"}}";
    private static final String SIGNED_IN = "{\"user_name\":\"lookup\",\"roles\":[\"readall\"]}";

    private final OpenSearchProvider provider = new OpenSearchProvider();

    private static ServiceConfig config(FakeOpenSearch node, String... values)
    {
        String[] all = new String[values.length + 2];
        all[0] = OpenSearchSettings.HOSTS;
        all[1] = "http://127.0.0.1:" + node.port();
        System.arraycopy(values, 0, all, 2, values.length);
        return OpenSearchSettingsTest.config(all);
    }

    @Test
    void describesOpenSearchInItsOwnTerms()
    {
        ServiceTypeDefinition definition = provider.definition();
        assertThat(definition.name()).isEqualTo("opensearch");
        assertThat(definition.resources()).extracting(ResourceDefinition::name).containsExactly("index", "field", "cluster");
        ResourceDefinition field = definition.resources().get(1);
        assertThat(field.parent()).isEqualTo("index");
        assertThat(field.accessTypes()).containsExactly("read");
        assertThat(definition.resources().get(0).accessTypes()).containsExactlyInAnyOrder("index", "delete", "create_index", "manage_aliases", "monitor",
                "manage");
        assertThat(definition.resources().get(2).accessTypes()).containsExactlyInAnyOrder("cluster_monitor", "manage_snapshots", "manage_templates",
                "manage_pipelines");
        assertThat(definition.resources()).allMatch(ResourceDefinition::caseSensitive).allMatch(ResourceDefinition::lookupSupported);
        AccessTypeDefinition manage = definition.accessTypes().stream().filter(type -> "manage".equals(type.name())).findFirst().orElseThrow();
        assertThat(manage.impliedGrants()).containsExactlyInAnyOrder("create_index", "manage_aliases", "monitor");
        assertThat(requireNonNull(definition.rowFilter()).resources()).containsExactly("index");
        assertThat(requireNonNull(definition.dataMask()).resources()).containsExactly("field");
        assertThat(requireNonNull(definition.dataMask()).maskTypes()).extracting(MaskTypeDefinition::name)
                .containsExactly("mask", "mask_show_last_4", "mask_show_first_4", "mask_hash", "mask_hash_salted", "mask_none", "custom");
        assertThat(requireNonNull(definition.dataMask()).maskTypes()).filteredOn(mask -> mask.transformer() != null)
                .allMatch(mask -> String.valueOf(mask.transformer()).startsWith("{col}"));
        assertThat(definition.configFields()).extracting(ConfigField::name).contains(OpenSearchSettings.HOSTS, OpenSearchSettings.KEY_PASSPHRASE);
    }

    @Test
    void validatesTheSettings()
    {
        assertThat(provider.validateConfig(OpenSearchSettingsTest.config())).isEmpty();
        assertThat(provider.validateConfig(OpenSearchSettingsTest.config(OpenSearchSettings.HOSTS, "ftp://x"))).hasSize(1);
    }

    @Test
    void theConnectionTestDescribesTheCluster() throws Exception
    {
        String resolved = "{\"indices\":[{\"name\":\"logs\"},{\"name\":\".ds-app-000001\",\"data_stream\":\"app\"}],\"data_streams\":[{\"name\":\"app\"}]}";
        try (FakeOpenSearch node = FakeOpenSearch.http().password("secret").answer("/", 200, ROOT).answer("/_plugins/_security/authinfo", 200, SIGNED_IN)
                .answer("/_resolve/index/*", 200, resolved)) {
            ConnectionResult result = provider.testConnection(config(node));
            assertThat(result.status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
            assertThat(result.message()).isEqualTo("OpenSearch 3.9.0, cluster search, signed in as lookup, 2 indices and data streams");
        }
        try (FakeOpenSearch node = FakeOpenSearch.http().password("secret").answer("/", 403, "{\"error\":{\"reason\":\"no permissions\"}}")
                .answer("/_plugins/_security/authinfo", 200, SIGNED_IN).answer("/_resolve/index/*", 200, "{\"indices\":[{\"name\":\"logs\"}]}")) {
            assertThat(provider.testConnection(config(node)).message())
                    .isEqualTo("OpenSearch (the user may not read the cluster's release, cluster:monitor/main), signed in as lookup, 1 index or data stream");
        }
    }

    @Test
    void theConnectionTestWantsOpenSearchWithItsSecurityPlugin() throws Exception
    {
        try (FakeOpenSearch node = FakeOpenSearch.http().password("secret").answer("/", 200,
                "{\"cluster_name\":\"x\",\"version\":{\"number\":\"8.15.0\",\"build_flavor\":\"default\"}}")) {
            assertThat(provider.testConnection(config(node)).message()).isEqualTo("the nodes at http://127.0.0.1:" + node.port() + " are no OpenSearch cluster");
        }
        try (FakeOpenSearch node = FakeOpenSearch.http().answer("/", 200, ROOT)) {
            ConnectionResult result = provider.testConnection(config(node));
            assertThat(result.status()).isEqualTo(ConnectionResult.Status.FAILED);
            assertThat(result.message()).isEqualTo("OpenSearch 3.9.0 runs without the security plugin, which GrantForge enforces policies with");
        }
        try (FakeOpenSearch node = FakeOpenSearch.http().answer("/", 403, "{\"error\":\"no\"}")) {
            assertThat(provider.testConnection(config(node)).message()).startsWith("the cluster runs without the security plugin");
        }
        try (FakeOpenSearch node = FakeOpenSearch.http().password("secret").answer("/", 500, "{\"error\":\"down\"}")
                .answer("/_plugins/_security/authinfo", 200, SIGNED_IN)) {
            assertThat(provider.testConnection(config(node)).message()).endsWith("500 down");
        }
    }

    @Test
    void theConnectionTestReportsUnusableSettingsAndRefusals() throws Exception
    {
        assertThat(provider.testConnection(new ServiceConfig("search", Map.of())).message()).startsWith(OpenSearchSettings.HOSTS + ": ");
        try (FakeOpenSearch node = FakeOpenSearch.http().password("other")) {
            assertThat(provider.testConnection(config(node)).message()).endsWith("401 Unauthorized");
        }
    }

    @Test
    void looksUpThroughTheCluster() throws Exception
    {
        try (FakeOpenSearch node = FakeOpenSearch.http().password("secret").answer("/", 200, ROOT)) {
            assertThat(provider.lookup(new LookupRequest(config(node), OpenSearchProvider.CLUSTER, "", Map.of(), 5))).containsExactly("search");
            assertThatThrownBy(() -> provider.lookup(new LookupRequest(config(node), "tenant", "", Map.of(), 5)))
                    .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
            assertThatThrownBy(() -> provider.lookup(new LookupRequest(new ServiceConfig("search", Map.of()), OpenSearchProvider.INDEX, "", Map.of(), 5)))
                    .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
            assertThatThrownBy(() -> provider.lookup(new LookupRequest(config(node), OpenSearchProvider.INDEX, "", Map.of(), 5)))
                    .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.NOT_FOUND));
        }
        assertThat(Set.of(OpenSearchProvider.TYPE)).containsExactly("opensearch");
        assertThat(List.of(OpenSearchProvider.READ, OpenSearchProvider.WRITE)).containsExactly("read", "index");
    }
}
