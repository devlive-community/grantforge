// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("NullAway")
class LookupRequestBodyTest
{
    @Test
    void leavesOutLevelsAndValuesWithoutValue()
    {
        Map<String, List<String>> context = new HashMap<>();
        context.put("database", new ArrayList<>(Arrays.asList("sales", null)));
        context.put("table", null);
        LookupRequestBody body = new LookupRequestBody("table", null, context, null);
        context.clear();
        Map<String, List<String>> copied = body.context();
        assertThat(copied).containsExactly(Map.entry("database", List.of("sales")));
        assertThat(new LookupRequestBody("database", null, null, 5).context()).isEmpty();
    }
}
