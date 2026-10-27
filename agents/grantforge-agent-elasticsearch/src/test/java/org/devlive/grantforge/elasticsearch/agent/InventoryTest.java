// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryTest
{
    private static final JsonNode RESOLVED = FakeElasticsearch.json("""
            {"indices": [{"name": "logs-eu"}, {"name": ".kibana_1"}, {"name": ".ds-app-000001", "data_stream": "app"}, {"name": ""}],
             "data_streams": [{"name": "app"}, {"name": ".hidden-stream"}]}""");
    private static final JsonNode MAPPINGS = FakeElasticsearch.json("""
            {"logs-eu": {"mappings": {"properties": {"name": {"type": "text", "fields": {"keyword": {"type": "keyword"}}},
               "address": {"properties": {"city": {"type": "keyword"}, "geo": {"type": "object"}}},
               "tags": {"type": "nested", "properties": {"label": {"type": "keyword"}}},
               "empty": {"type": "nested"}, "untyped": {}, "alias": {"type": "alias", "path": "name"}}}},
             "logs-us": {"mappings": {"properties": {"amount": {"type": "long"}}}}}""");

    @Test
    void namesIndicesAndDataStreamsButBackingAndDotIndices()
    {
        Inventory inventory = Inventory.of("search", RESOLVED, target -> MAPPINGS);
        assertThat(inventory.cluster()).isEqualTo("search");
        assertThat(inventory.names()).containsExactly("app", "logs-eu");
        assertThat(inventory.named(".ds-app-000001")).isEqualTo("app");
        assertThat(inventory.named("logs-eu")).isEqualTo("logs-eu");
    }

    @Test
    void readsTheFieldsOfValuesOncePerTarget()
    {
        List<String> asked = new ArrayList<>();
        Inventory inventory = Inventory.of("search", RESOLVED, target -> {
            asked.add(target);
            return MAPPINGS;
        });
        assertThat(inventory.fields("logs-*")).as("objects, nested containers and multi-fields are left out")
                .containsExactly("address.city", "alias", "amount", "name", "tags.label");
        assertThat(inventory.fields("logs-*")).hasSize(5);
        assertThat(asked).containsExactly("logs-*");
        assertThat(Inventory.EMPTY.names()).isEmpty();
        assertThat(Inventory.EMPTY.fields("x")).isEmpty();
        assertThat(Inventory.EMPTY.cluster()).isEmpty();
    }
}
