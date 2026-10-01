// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LookupRequestTest
{
    private static final ServiceConfig CONFIG = new ServiceConfig("hive", Map.of());

    @Test
    void lookupRequestsCopyTheirContext()
    {
        Map<String, List<String>> context = new HashMap<>();
        context.put("database", new ArrayList<>(List.of("sales")));
        LookupRequest request = new LookupRequest(CONFIG, "table", "ord", context, 20);
        context.get("database").add("hr");
        context.put("other", List.of());

        assertThat(request.context()).containsExactly(Map.entry("database", List.of("sales")));
        assertThatThrownBy(() -> new LookupRequest(CONFIG, "table", "", Map.of(), 0)).hasMessageContaining("limit");
    }
}
