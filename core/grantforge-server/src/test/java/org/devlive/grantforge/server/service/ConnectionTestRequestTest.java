// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NullAway")
class ConnectionTestRequestTest
{
    @Test
    void leavesOutSettingsWithoutValue()
    {
        Map<String, String> given = new HashMap<>();
        given.put("url", "demo://dw");
        given.put("password", null);
        Map<String, String> values = new ConnectionTestRequest("demo", null, null, given).values();
        assertThat(values).containsExactly(Map.entry("url", "demo://dw"));
        assertThat(new ConnectionTestRequest("demo", "7", "warehouse", null).values()).isEmpty();
    }
}
