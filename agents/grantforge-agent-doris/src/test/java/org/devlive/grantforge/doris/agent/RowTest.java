// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RowTest
{
    @Test
    void readsTextNumbersFlagsAndMoments()
    {
        LocalDateTime at = LocalDateTime.of(2026, 10, 27, 11, 54, 18, 860_000_000);
        Row row = Row.of("Name", "orders", "count", 3L, "flag", true, "text", "7", "none", null, "at", at, "stamp", Timestamp.valueOf(at),
                "written", "2026-10-27 11:54:18.860");
        assertThat(row.first()).as("the first column, as SHOW statements give names").isEqualTo("orders");
        assertThat(row.text("Name")).isEqualTo("orders");
        assertThat(row.text("none")).isNull();
        assertThat(row.string("none")).isEmpty();
        assertThat(row.number("count")).isEqualTo(3);
        assertThat(row.number("text")).isEqualTo(7);
        assertThat(row.number("flag")).isEqualTo(1);
        assertThat(row.number("none")).isZero();
        assertThat(row.flag("flag")).isTrue();
        assertThat(row.moment("at")).isEqualTo(at);
        assertThat(row.moment("stamp")).isEqualTo(at);
        assertThat(row.moment("written")).isEqualTo(at);
        assertThat(row.moment("none")).isNull();
        assertThat(row.toString()).contains("orders");
    }

    @Test
    void readsNothingFromARowWithoutColumns()
    {
        assertThat(Row.of().first()).isEmpty();
        assertThat(Row.of("Name", null).first()).isEmpty();
    }
}
