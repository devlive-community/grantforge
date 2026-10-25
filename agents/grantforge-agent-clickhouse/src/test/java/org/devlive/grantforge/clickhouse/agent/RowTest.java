// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RowTest
{
    @Test
    void readsTextNumbersFlagsAndArrays()
    {
        Row row = Row.of("name", "alice", "count", 3L, "small", (short) 1, "yes", true, "no", false, "digits", "42", "none", null, "list",
                List.of("a", "b"), "array", new String[] {"c"}, "other", 7);
        assertThat(row.text("name")).isEqualTo("alice");
        assertThat(row.text("none")).isNull();
        assertThat(row.text("missing")).isNull();
        assertThat(row.string("none")).isEmpty();
        assertThat(row.number("count")).isEqualTo(3);
        assertThat(row.number("digits")).isEqualTo(42);
        assertThat(row.number("none")).isZero();
        assertThat(row.flag("small")).isTrue();
        assertThat(row.flag("yes")).isTrue();
        assertThat(row.flag("no")).isFalse();
        assertThat(row.texts("list")).containsExactly("a", "b");
        assertThat(row.texts("array")).containsExactly("c");
        assertThat(row.texts("other")).isEmpty();
        assertThat(row.texts("none")).isEmpty();
        assertThat(row.toString()).contains("alice");
    }
}
