// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.common.util.NamedList;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SolrProviderTest
{
    private final SolrClient solr = mock(SolrClient.class);
    private final SolrProvider provider = new SolrProvider(settings -> new SolrNodeClient(settings, (url, ignored) -> solr));

    private static ResourceDefinition resource(ServiceTypeDefinition definition, String name)
    {
        return definition.resources().stream().filter(resource -> resource.name().equals(name)).findFirst().orElseThrow();
    }

    private static String implied(ServiceTypeDefinition definition, String accessType)
    {
        return definition.accessTypes().stream().filter(type -> type.name().equals(accessType)).findFirst().map(AccessTypeDefinition::impliedGrants)
                .orElseThrow().toString();
    }

    private static NamedList<Object> list(Object... pairs)
    {
        NamedList<Object> list = new NamedList<>();
        for (int index = 0; index < pairs.length; index += 2) {
            list.add(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return list;
    }

    private void answer(String mode, NamedList<Object> collections) throws Exception
    {
        doAnswer(call -> {
            String path = call.<SolrRequest<?>>getArgument(0).getPath();
            return "/admin/info/system".equals(path) ? list("mode", mode, "lucene", list("solr-spec-version", "9.11.0")) : collections;
        }).when(solr).request(any(), any());
    }

    @Test
    void definesSolrsLevelsAndPermissions()
    {
        ServiceTypeDefinition definition = new SolrProvider().definition();
        assertThat(definition.name()).isEqualTo("solr");
        assertThat(definition.resources()).extracting(ResourceDefinition::name).containsExactly("collection", "path", "cluster");
        assertThat(resource(definition, "path").parent()).isEqualTo("collection");
        assertThat(resource(definition, "collection").accessTypes()).contains("query", "update", "admin_collection").doesNotContain("admin");
        assertThat(resource(definition, "path").accessTypes()).contains("query", "update").doesNotContain("admin_collection");
        assertThat(resource(definition, "cluster").accessTypes()).containsExactlyInAnyOrder("health", "metrics", "admin_read", "security", "admin");
        assertThat(implied(definition, "schema_edit")).contains("schema_read");
        assertThat(implied(definition, "config_edit")).contains("config_read");
        assertThat(implied(definition, "admin_collection")).contains("query", "update", "schema_edit", "config_edit");
        assertThat(implied(definition, "admin_read")).contains("health", "metrics");
        assertThat(implied(definition, "admin")).contains("admin_read", "security", "admin_collection");
        assertThat(definition.conditions()).singleElement().satisfies(condition -> assertThat(condition.name()).isEqualTo("ip-range"));
        assertThat(definition.configFields()).extracting(ConfigField::name).containsExactly(SolrSettings.URLS, SolrSettings.AUTHENTICATION,
                SolrSettings.USER, SolrSettings.PASSWORD, SolrSettings.TOKEN_FIELD, SolrSettings.TRUST_STORE, SolrSettings.TRUST_STORE_PASSWORD,
                SolrSettings.TRUST_STORE_TYPE, SolrSettings.TIMEOUT);
    }

    @Test
    void checksSettings()
    {
        assertThat(provider.validateConfig(SolrSettingsTest.config())).isEmpty();
        assertThat(provider.validateConfig(SolrSettingsTest.config(SolrSettings.URLS, "nowhere"))).isNotEmpty();
    }

    @Test
    void describesTheCluster() throws Exception
    {
        answer("solrcloud", list("collections", List.of("sales")));
        assertThat(provider.testConnection(SolrSettingsTest.config()).message()).isEqualTo("Solr 9.11.0 in SolrCloud mode, 1 collection");
        answer("solrcloud", list("collections", List.of("sales", "hr")));
        assertThat(provider.testConnection(SolrSettingsTest.config()).message()).isEqualTo("Solr 9.11.0 in SolrCloud mode, 2 collections");
        answer("std", list("status", list("products", list())));
        assertThat(provider.testConnection(SolrSettingsTest.config()).message()).isEqualTo("Solr 9.11.0 without SolrCloud, 1 core");
        answer("std", list("status", list("products", list(), "orders", list())));
        ConnectionResult cores = provider.testConnection(SolrSettingsTest.config());
        assertThat(cores.status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(cores.message()).endsWith("2 cores");
    }

    @Test
    void failsTheTestWithWhyItFailed() throws Exception
    {
        when(solr.request(any(), any())).thenThrow(new SolrServerException("down", new ConnectException("Connection refused")));
        ConnectionResult result = provider.testConnection(SolrSettingsTest.config());
        assertThat(result.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(result.message()).isEqualTo("down");
        assertThat(provider.testConnection(SolrSettingsTest.config(SolrSettings.TIMEOUT, "0")).message()).startsWith("timeout: ");
    }

    @Test
    void looksUpCollectionsAndTheCluster() throws Exception
    {
        answer("solrcloud", list("collections", List.of("sales", "hr")));
        assertThat(provider.lookup(new LookupRequest(SolrSettingsTest.config(), "collection", "", Map.of(), 10))).containsExactly("hr", "sales");
        assertThat(provider.lookup(new LookupRequest(SolrSettingsTest.config(), "cluster", "", Map.of(), 10))).containsExactly("*");
        assertThat(provider.lookup(new LookupRequest(SolrSettingsTest.config(), "cluster", "x", Map.of(), 10))).isEmpty();
    }

    @Test
    void namesWhyALookupFails() throws Exception
    {
        when(solr.request(any(), any())).thenThrow(new BaseHttpSolrClient.RemoteSolrException("http://s1", 403, "Unauthorized request", null));
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(SolrSettingsTest.config(), "collection", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED));
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(SolrSettingsTest.config(), "core", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(SolrSettingsTest.config(SolrSettings.URLS, "x"), "collection", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
    }
}
