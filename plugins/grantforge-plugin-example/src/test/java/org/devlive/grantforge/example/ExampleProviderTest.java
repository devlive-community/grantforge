// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.example;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExampleProviderTest
{
    private final ExampleProvider provider = new ExampleProvider();

    private static ServiceConfig config(Map<String, String> values)
    {
        return new ServiceConfig("dw", values);
    }

    @Test
    void declaresEveryKindOfPolicy()
    {
        ServiceTypeDefinition definition = provider.definition();
        assertThat(definition.name()).isEqualTo("example");
        assertThat(definition.policyTypes()).containsExactlyInAnyOrder(PolicyType.ACCESS, PolicyType.DATA_MASK, PolicyType.ROW_FILTER);
        assertThat(definition.hierarchies()).containsExactly(List.of("database", "table", "column"), List.of("path"));
        assertThat(definition.configFields()).hasSize(3);
    }

    @Test
    void checksSettingsAndConnects()
    {
        assertThat(provider.validateConfig(config(Map.of("timeout", "30")))).isEmpty();
        assertThat(provider.validateConfig(config(Map.of()))).isEmpty();
        assertThat(provider.validateConfig(config(Map.of("timeout", "601")))).singleElement()
                .satisfies(problem -> assertThat(problem.field()).isEqualTo("timeout"));
        assertThat(provider.testConnection(config(Map.of("password", ExampleProvider.PASSWORD))).status())
                .isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(provider.testConnection(config(Map.of())).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }

    @Test
    void looksUpDatabasesAndTheirTables()
    {
        ServiceConfig config = config(Map.of());
        assertThat(provider.lookup(new LookupRequest(config, "database", "", Map.of(), 10))).containsExactly("hr", "ops", "sales");
        assertThat(provider.lookup(new LookupRequest(config, "database", "s", Map.of(), 10))).containsExactly("sales");
        assertThat(provider.lookup(new LookupRequest(config, "table", "", Map.of("database", List.of("sales", "nowhere")), 1)))
                .containsExactly("orders");
        assertThat(provider.lookup(new LookupRequest(config, "table", "", Map.of(), 10))).isEmpty();
        assertThat(provider.lookup(new LookupRequest(config, "path", "", Map.of(), 10))).isEmpty();
    }
}
