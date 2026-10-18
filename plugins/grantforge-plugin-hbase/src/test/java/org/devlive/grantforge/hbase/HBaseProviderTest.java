// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase;

import org.apache.hadoop.hbase.ClusterMetrics;
import org.apache.hadoop.hbase.ServerMetrics;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HBaseProviderTest
{
    private final Connection connection = mock(Connection.class);
    private final Admin admin = mock(Admin.class);
    private final HBaseProvider provider = new HBaseProvider(settings -> new HBaseClient(settings, (configuration, user) -> connection));

    private static ResourceDefinition resource(ServiceTypeDefinition definition, String name)
    {
        return definition.resources().stream().filter(resource -> resource.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void definesHBasesScopesAndPermissions()
    {
        ServiceTypeDefinition definition = new HBaseProvider().definition();
        assertThat(definition.name()).isEqualTo("hbase");
        assertThat(definition.resources()).extracting(ResourceDefinition::name).containsExactly("global", "namespace", "table", "column_family",
                "column");
        assertThat(resource(definition, "global").accessTypes()).containsExactly("admin");
        assertThat(resource(definition, "namespace").parent()).isNull();
        assertThat(resource(definition, "table").parent()).isEqualTo("namespace");
        assertThat(resource(definition, "column_family").parent()).isEqualTo("table");
        assertThat(resource(definition, "column").parent()).isEqualTo("column_family");
        assertThat(resource(definition, "table").accessTypes()).containsExactlyInAnyOrder("read", "write", "create", "admin");
        assertThat(resource(definition, "column").accessTypes()).containsExactlyInAnyOrder("read", "write");
        assertThat(resource(definition, "column").lookupSupported()).isFalse();
        assertThat(resource(definition, "table").caseSensitive()).isTrue();
        assertThat(definition.accessTypes()).extracting(AccessTypeDefinition::name).containsExactly("read", "write", "create", "admin");
        assertThat(definition.configFields()).extracting(ConfigField::name).containsExactly(HBaseSettings.QUORUM, HBaseSettings.ZOOKEEPER_PORT,
                HBaseSettings.ZNODE, HBaseSettings.BOOTSTRAP, HBaseSettings.AUTHENTICATION, HBaseSettings.USER, HBaseSettings.PASSWORD,
                HBaseSettings.KEYTAB, HBaseSettings.KDC, HBaseSettings.MASTER_PRINCIPAL, HBaseSettings.REGION_SERVER_PRINCIPAL,
                HBaseSettings.PROTECTION, HBaseSettings.TIMEOUT);
    }

    @Test
    void checksSettings()
    {
        assertThat(provider.validateConfig(HBaseSettingsTest.config())).isEmpty();
        assertThat(provider.validateConfig(HBaseSettingsTest.config(HBaseSettings.USER, ""))).isNotEmpty();
    }

    @Test
    void describesTheClusterItConnectsTo() throws Exception
    {
        when(connection.getAdmin()).thenReturn(admin);
        ClusterMetrics metrics = mock(ClusterMetrics.class);
        when(metrics.getClusterId()).thenReturn("c-1");
        when(metrics.getHBaseVersion()).thenReturn("2.6.7");
        when(metrics.getLiveServerMetrics()).thenReturn(Map.of(ServerName.valueOf("rs1", 16020, 1), mock(ServerMetrics.class),
                ServerName.valueOf("rs2", 16020, 1), mock(ServerMetrics.class)));
        when(admin.getClusterMetrics(any())).thenReturn(metrics);
        ConnectionResult result = provider.testConnection(HBaseSettingsTest.config());
        assertThat(result.status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(result.message()).isEqualTo("cluster c-1, HBase 2.6.7, 2 region servers");
        when(metrics.getLiveServerMetrics()).thenReturn(Map.of(ServerName.valueOf("rs1", 16020, 1), mock(ServerMetrics.class)));
        assertThat(provider.testConnection(HBaseSettingsTest.config()).message()).endsWith(", 1 region server");
    }

    @Test
    void failsTheConnectionTestWithTheReason() throws Exception
    {
        when(connection.getAdmin()).thenThrow(new IOException("refused\nmore"));
        assertThat(provider.testConnection(HBaseSettingsTest.config())).satisfies(result -> {
            assertThat(result.status()).isEqualTo(ConnectionResult.Status.FAILED);
            assertThat(result.message()).isEqualTo("refused");
        });
        assertThat(provider.testConnection(HBaseSettingsTest.config(HBaseSettings.USER, ""))).satisfies(result -> {
            assertThat(result.status()).isEqualTo(ConnectionResult.Status.FAILED);
            assertThat(result.message()).startsWith("username: ");
        });
    }

    @Test
    void looksUpThroughTheClient() throws Exception
    {
        when(connection.getAdmin()).thenReturn(admin);
        when(admin.listNamespaceDescriptors()).thenReturn(new org.apache.hadoop.hbase.NamespaceDescriptor[] {
                org.apache.hadoop.hbase.NamespaceDescriptor.create("sales").build()});
        ServiceConfig config = HBaseSettingsTest.config();
        assertThat(provider.lookup(new LookupRequest(config, "namespace", "s", Map.of(), 10))).containsExactly("sales");
        assertThat(provider.lookup(new LookupRequest(config, "global", "", Map.of(), 10))).containsExactly("*");
        assertThat(provider.lookup(new LookupRequest(config, "global", "x", Map.of(), 10))).isEmpty();
    }

    @Test
    void refusesLookupsItCannotMake()
    {
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(HBaseSettingsTest.config(HBaseSettings.USER, ""), "namespace", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(HBaseSettingsTest.config(), "column", "", Map.of("table", List.of("t")), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
    }
}
