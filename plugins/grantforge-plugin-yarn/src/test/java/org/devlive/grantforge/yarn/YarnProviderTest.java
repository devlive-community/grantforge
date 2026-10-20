// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn;

import org.apache.hadoop.yarn.api.records.QueueInfo;
import org.apache.hadoop.yarn.api.records.YarnClusterMetrics;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class YarnProviderTest
{
    private final YarnClient yarn = mock(YarnClient.class);
    private final YarnProvider provider = new YarnProvider(settings -> new YarnClusterClient(settings, configuration -> yarn));

    private static ResourceDefinition resource(ServiceTypeDefinition definition, String name)
    {
        return definition.resources().stream().filter(resource -> resource.name().equals(name)).findFirst().orElseThrow();
    }

    private static QueueInfo queue(String name)
    {
        QueueInfo queue = mock(QueueInfo.class);
        when(queue.getQueueName()).thenReturn(name);
        return queue;
    }

    @Test
    void definesYarnsLevelsAndPermissions()
    {
        ServiceTypeDefinition definition = new YarnProvider().definition();
        assertThat(definition.name()).isEqualTo("yarn");
        assertThat(definition.resources()).extracting(ResourceDefinition::name).containsExactly("queue", "application", "cluster");
        assertThat(resource(definition, "queue").validLeaf()).isTrue();
        assertThat(resource(definition, "queue").accessTypes()).containsExactlyInAnyOrder("submit_app", "admin_queue");
        assertThat(resource(definition, "application").parent()).isEqualTo("queue");
        assertThat(resource(definition, "application").accessTypes()).containsExactly("submit_app");
        assertThat(resource(definition, "cluster").accessTypes()).containsExactly("admin");
        assertThat(definition.accessTypes()).filteredOn(type -> type.name().equals("admin_queue")).singleElement()
                .extracting(AccessTypeDefinition::impliedGrants).asString().contains("submit_app");
        assertThat(definition.conditions()).singleElement().satisfies(condition -> assertThat(condition.name()).isEqualTo("ip-range"));
        assertThat(definition.configFields()).extracting(ConfigField::name).containsExactly(YarnSettings.RESOURCE_MANAGER, YarnSettings.AUTHENTICATION,
                YarnSettings.USER, YarnSettings.PASSWORD, YarnSettings.KEYTAB, YarnSettings.KDC, YarnSettings.PRINCIPAL, YarnSettings.PROTECTION,
                YarnSettings.TIMEOUT);
    }

    @Test
    void checksSettings()
    {
        assertThat(provider.validateConfig(YarnSettingsTest.config())).isEmpty();
        assertThat(provider.validateConfig(YarnSettingsTest.config(YarnSettings.USER, ""))).isNotEmpty();
    }

    @Test
    void describesTheCluster() throws Exception
    {
        YarnClusterMetrics metrics = mock(YarnClusterMetrics.class);
        when(metrics.getNumNodeManagers()).thenReturn(3);
        when(yarn.getYarnClusterMetrics()).thenReturn(metrics);
        List<QueueInfo> roots = List.of(queue("default"));
        when(yarn.getRootQueueInfos()).thenReturn(roots);
        assertThat(provider.testConnection(YarnSettingsTest.config())).isEqualTo(new ConnectionResult(ConnectionResult.Status.SUCCEEDED,
                "3 NodeManagers, 2 queues"));
        when(metrics.getNumNodeManagers()).thenReturn(1);
        when(yarn.getRootQueueInfos()).thenReturn(List.of());
        assertThat(provider.testConnection(YarnSettingsTest.config()).message()).isEqualTo("1 NodeManager, 1 queue");
    }

    @Test
    void namesWhyTheConnectionFailed() throws Exception
    {
        when(yarn.getYarnClusterMetrics()).thenThrow(new IOException("down", new ConnectException("refused")));
        ConnectionResult failed = provider.testConnection(YarnSettingsTest.config());
        assertThat(failed.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(failed.message()).isEqualTo("down");
        assertThat(provider.testConnection(YarnSettingsTest.config(YarnSettings.RESOURCE_MANAGER, "rm:0")).message())
                .startsWith("yarn.resourcemanager.address: ");
    }

    @Test
    void looksUpThroughTheResourceManagerAndAnswersTheClusterItself() throws Exception
    {
        List<QueueInfo> roots = List.of(queue("default"));
        when(yarn.getRootQueueInfos()).thenReturn(roots);
        assertThat(provider.lookup(new LookupRequest(YarnSettingsTest.config(), "queue", "root.", Map.of(), 10))).containsExactly("root.default");
        assertThat(provider.lookup(new LookupRequest(YarnSettingsTest.config(), "cluster", "", Map.of(), 10))).containsExactly("*");
        assertThat(provider.lookup(new LookupRequest(YarnSettingsTest.config(), "cluster", "x", Map.of(), 10))).isEmpty();
    }

    @Test
    void refusesWhatItCannotLookUp()
    {
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(YarnSettingsTest.config(), "user", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(YarnSettingsTest.config(YarnSettings.TIMEOUT, "0"), "queue", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getMessage()).startsWith("timeout: "));
    }
}
