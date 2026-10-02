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
class ServiceRequestTest
{
    @Test
    void leavesOutSettingsWithoutValueAndKeepsItsOwnCopy()
    {
        Map<String, String> given = new HashMap<>();
        given.put("url", "demo://dw");
        given.put("password", null);
        ServiceRequest request = new ServiceRequest("demo", " warehouse ", "Warehouse", null, null, given);
        given.put("colour", "red");
        Map<String, String> values = request.values();
        assertThat(values).containsExactly(Map.entry("url", "demo://dw"));
        assertThat(request.command().name()).isEqualTo("warehouse");
        assertThat(request.command().enabled()).isTrue();

        ServiceRequest none = new ServiceRequest("demo", "warehouse", "Warehouse", null, false, null);
        assertThat(none.values()).isEmpty();
        assertThat(none.command().enabled()).isFalse();
    }
}
