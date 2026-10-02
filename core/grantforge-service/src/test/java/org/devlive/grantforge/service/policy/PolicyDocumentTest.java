// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyDocumentTest
{
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void readsPartialJsonAndWritesItBack()
    {
        PolicyDocument read = JSON.readValue("""
                {"resources": {" database ": {"values": ["sales"], "excludes": false, "recursive": false}, "table": null},
                 "allow": [{"users": ["alice"], "accessTypes": ["select"]}, null],
                 "validity": [{"from": "2026-01-01T00:00:00Z"}]}
                """, PolicyDocument.class);
        assertThat(read.resources()).containsExactly(Map.entry("database", ResourceValues.of("sales")));
        assertThat(read.allow()).containsExactly(PolicyItemSpec.access(List.of("alice"), List.of(), List.of("select")));
        assertThat(read.deny()).isEmpty();
        assertThat(read.validity()).containsExactly(new ValidityPeriod(Instant.parse("2026-01-01T00:00:00Z"), null));
        assertThat(JSON.readValue(JSON.writeValueAsString(read), PolicyDocument.class)).isEqualTo(read);
        assertThat(JSON.readValue("{}", PolicyDocument.class).resources()).isEmpty();
    }

    @Test
    void keepsItsOwnCopyOfTheLevelsInTheirOrder()
    {
        Map<String, ResourceValues> levels = new HashMap<>();
        levels.put("database", ResourceValues.of("x"));
        PolicyDocument document = PolicyDocument.allowing(levels);
        levels.put("table", ResourceValues.of("y"));
        assertThat(document.resources()).containsOnlyKeys("database");
        assertThat(document.allow()).isEmpty();
    }
}
