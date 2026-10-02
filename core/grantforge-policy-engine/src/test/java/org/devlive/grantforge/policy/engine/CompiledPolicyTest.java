// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CompiledPolicyTest
{
    @Test
    void coversRequestsDownToItsDeepestLevel()
    {
        Policy policy = Policy.builder(1).resource("database", ResourceSpec.of("sales")).resource("table", ResourceSpec.of("*"))
                .resource("column", ResourceSpec.of(List.of("secret"), true, false)).build();
        CompiledPolicy compiled = new CompiledPolicy(policy, Models.HIVE);
        assertThat(compiled.root()).isEqualTo("database");
        assertThat(compiled.policy()).isSameAs(policy);
        assertThat(compiled.rootLevel().excludes()).isFalse();

        Map<String, String> column = new LinkedHashMap<>();
        column.put("database", "sales");
        column.put("table", "orders");
        column.put("column", "amount");
        assertThat(compiled.covers(column)).isTrue();
        column.put("column", "secret");
        assertThat(compiled.covers(column)).isFalse();
        // The column level is an exclusion, not *, so a table-wide request is not covered.
        column.remove("column");
        assertThat(compiled.covers(column)).isFalse();
        assertThat(compiled.covers(Map.of("url", "x"))).isFalse();
    }
}
