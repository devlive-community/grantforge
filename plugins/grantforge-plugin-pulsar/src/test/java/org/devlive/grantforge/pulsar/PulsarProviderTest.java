// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.pulsar;

import org.apache.pulsar.client.admin.Clusters;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.admin.Tenants;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PulsarProviderTest
{
    private final PulsarAdmin admin = mock(PulsarAdmin.class);
    private final Tenants tenants = mock(Tenants.class);
    private final Clusters clusters = mock(Clusters.class);
    private final PulsarProvider provider = new PulsarProvider(settings -> new PulsarAdminClient(settings, ignored -> admin));

    @BeforeEach
    void cluster()
    {
        when(admin.tenants()).thenReturn(tenants);
        when(admin.clusters()).thenReturn(clusters);
    }

    private static ResourceDefinition resource(ServiceTypeDefinition definition, String name)
    {
        return definition.resources().stream().filter(resource -> resource.name().equals(name)).findFirst().orElseThrow();
    }

    private static String implied(ServiceTypeDefinition definition, String accessType)
    {
        return definition.accessTypes().stream().filter(type -> type.name().equals(accessType)).findFirst().map(AccessTypeDefinition::impliedGrants)
                .orElseThrow().toString();
    }

    @Test
    void definesPulsarsLevelsAndActions()
    {
        ServiceTypeDefinition definition = new PulsarProvider().definition();
        assertThat(definition.name()).isEqualTo("pulsar");
        assertThat(definition.resources()).extracting(ResourceDefinition::name).containsExactly("tenant", "namespace", "topic", "subscription", "cluster");
        assertThat(resource(definition, "namespace").parent()).isEqualTo("tenant");
        assertThat(resource(definition, "topic").parent()).isEqualTo("namespace");
        assertThat(resource(definition, "subscription").parent()).isEqualTo("topic");
        assertThat(resource(definition, "subscription").accessTypes()).containsExactly("consume");
        assertThat(resource(definition, "topic").accessTypes()).containsExactlyInAnyOrder("produce", "consume", "admin_topic");
        assertThat(resource(definition, "tenant").accessTypes()).contains("admin_tenant", "functions").doesNotContain("admin");
        assertThat(resource(definition, "cluster").accessTypes()).containsExactly("admin");
        assertThat(implied(definition, "admin_topic")).contains("produce", "consume");
        assertThat(implied(definition, "admin_namespace")).contains("admin_topic");
        assertThat(implied(definition, "admin_tenant")).contains("admin_namespace", "functions", "sources", "sinks", "packages");
        assertThat(implied(definition, "admin")).contains("admin_tenant");
        assertThat(definition.conditions()).singleElement().satisfies(condition -> assertThat(condition.name()).isEqualTo("ip-range"));
        assertThat(definition.configFields()).extracting(ConfigField::name).containsExactly(PulsarSettings.WEB_SERVICE_URL, PulsarSettings.AUTHENTICATION,
                PulsarSettings.TOKEN_FIELD, PulsarSettings.USER, PulsarSettings.PASSWORD, PulsarSettings.ISSUER_URL, PulsarSettings.PRIVATE_KEY,
                PulsarSettings.AUDIENCE, PulsarSettings.SCOPE, PulsarSettings.CERTIFICATE, PulsarSettings.KEY, PulsarSettings.TRUST_CERTS,
                PulsarSettings.HOSTNAME_VERIFICATION, PulsarSettings.TIMEOUT);
    }

    @Test
    void checksSettings()
    {
        assertThat(provider.validateConfig(PulsarSettingsTest.config())).isEmpty();
        assertThat(provider.validateConfig(PulsarSettingsTest.config(PulsarSettings.WEB_SERVICE_URL, "nowhere"))).isNotEmpty();
    }

    @Test
    void describesTheCluster() throws PulsarAdminException
    {
        when(clusters.getClusters()).thenReturn(List.of("standalone"));
        when(tenants.getTenants()).thenReturn(List.of("public"));
        ConnectionResult result = provider.testConnection(PulsarSettingsTest.config());
        assertThat(result.status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(result.message()).isEqualTo("cluster standalone, 1 tenant");
        when(clusters.getClusters()).thenReturn(List.of("west", "east"));
        when(tenants.getTenants()).thenReturn(List.of("public", "sales"));
        assertThat(provider.testConnection(PulsarSettingsTest.config()).message()).isEqualTo("clusters west, east, 2 tenants");
    }

    @Test
    void failsTheTestWithWhyItFailed() throws PulsarAdminException
    {
        when(clusters.getClusters()).thenThrow(new PulsarAdminException(new ConnectException("Connection refused"), "Connection refused", 0));
        ConnectionResult result = provider.testConnection(PulsarSettingsTest.config());
        assertThat(result.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(result.message()).contains("Connection refused");
        assertThat(provider.testConnection(PulsarSettingsTest.config(PulsarSettings.TIMEOUT, "0")).message()).startsWith("timeout: ");
    }

    @Test
    void looksUpTenants() throws PulsarAdminException
    {
        when(tenants.getTenants()).thenReturn(List.of("sales", "public"));
        assertThat(provider.lookup(new LookupRequest(PulsarSettingsTest.config(), "tenant", "", Map.of(), 10))).containsExactly("public", "sales");
    }

    @Test
    void namesWhyALookupFails() throws PulsarAdminException
    {
        when(tenants.getTenants()).thenThrow(new PulsarAdminException(null, "Client is not authorized", 401));
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(PulsarSettingsTest.config(), "tenant", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED));
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(PulsarSettingsTest.config(), "queue", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(PulsarSettingsTest.config(PulsarSettings.WEB_SERVICE_URL, "x"), "tenant", "",
                Map.of(), 10))).isInstanceOfSatisfying(LookupException.class,
                        failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
    }
}
