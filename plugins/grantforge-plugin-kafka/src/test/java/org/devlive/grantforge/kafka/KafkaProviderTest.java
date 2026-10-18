// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.AccessTypeDefinition;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.MatcherType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaProviderTest
{
    private final KafkaProvider provider = new KafkaProvider();

    @Test
    void definesKafkasResourceTypesAndOperations()
    {
        ServiceTypeDefinition type = provider.definition();
        assertThat(type.name()).isEqualTo("kafka");
        Map<String, ResourceDefinition> resources = type.resources().stream().collect(Collectors.toMap(ResourceDefinition::name,
                Function.identity()));
        assertThat(resources).containsOnlyKeys("topic", "group", "transactional_id", "cluster", "delegation_token", "user");
        assertThat(resources.values()).allSatisfy(resource -> {
            assertThat(resource.matcher()).isEqualTo(MatcherType.WILDCARD);
            assertThat(resource.caseSensitive()).isTrue();
            assertThat(resource.parent()).isNull();
            assertThat(resource.lookupSupported()).isTrue();
        });
        assertThat(requireNonNull(resources.get("topic")).accessTypes()).containsExactlyInAnyOrder("read", "write", "create", "delete", "alter", "describe",
                "describe_configs", "alter_configs", "all");
        assertThat(requireNonNull(resources.get("group")).accessTypes()).containsExactlyInAnyOrder("read", "delete", "describe", "all");
        assertThat(requireNonNull(resources.get("transactional_id")).accessTypes()).containsExactlyInAnyOrder("write", "describe", "all");
        assertThat(requireNonNull(resources.get("cluster")).accessTypes()).contains("idempotent_write", "cluster_action").doesNotContain("read", "write");
        assertThat(requireNonNull(resources.get("user")).accessTypes()).containsExactlyInAnyOrder("create_tokens", "describe_tokens", "all");
        Map<String, AccessTypeDefinition> access = type.accessTypes().stream().collect(Collectors.toMap(AccessTypeDefinition::name,
                Function.identity()));
        assertThat(requireNonNull(access.get("read")).impliedGrants()).containsExactly("describe");
        assertThat(requireNonNull(access.get("alter_configs")).impliedGrants()).containsExactly("describe_configs");
        assertThat(requireNonNull(access.get("create")).impliedGrants()).isEmpty();
        assertThat(requireNonNull(access.get("all")).impliedGrants()).hasSize(12);
        assertThat(type.configFields()).extracting(ConfigField::name).contains("bootstrap.servers", "security.protocol", "sasl.mechanism",
                "username", "password", "keytab", "kerberos.kdc", "ssl.truststore.location", "request.timeout.ms");
        assertThat(type.dataMask()).isNull();
        assertThat(type.rowFilter()).isNull();
    }

    @Test
    void checksTheSettings()
    {
        assertThat(provider.validateConfig(KafkaSettingsTest.config())).isEmpty();
        assertThat(provider.validateConfig(KafkaSettingsTest.config(KafkaSettings.PROTOCOL, "SASL_SSL"))).isNotEmpty();
    }

    @Test
    void anUnusableServiceFailsItsTestAndLookups()
    {
        ConnectionResult result = provider.testConnection(new ServiceConfig("x", Map.of(KafkaSettings.SERVERS, "kafka1")));
        assertThat(result.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(result.message()).contains("bootstrap.servers");
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(new ServiceConfig("x", Map.of(KafkaSettings.SERVERS, "kafka1")), "topic", "",
                Map.of(), 10))).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(KafkaSettingsTest.config(), "partition", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class,
                        failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
    }

    @Test
    void theClusterIsAlwaysNamedKafkaCluster()
    {
        assertThat(provider.lookup(new LookupRequest(KafkaSettingsTest.config(), "cluster", "kaf", Map.of(), 10)))
                .containsExactly("kafka-cluster");
        assertThat(provider.lookup(new LookupRequest(KafkaSettingsTest.config(), "cluster", "x", Map.of(), 10))).isEqualTo(List.of());
    }

    @Test
    void unreachableBrokersFailTheTestQuickly()
    {
        ConnectionResult result = provider.testConnection(KafkaSettingsTest.config(KafkaSettings.SERVERS, "127.0.0.1:1",
                KafkaSettings.TIMEOUT, "1500"));
        assertThat(result.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(result.message()).isNotBlank();
    }
}
