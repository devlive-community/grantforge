// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase.agent;

import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnFilterTest
{
    private static KeyValue cell(String row, String family, String qualifier)
    {
        return new KeyValue(Bytes.toBytes(row), Bytes.toBytes(family), Bytes.toBytes(qualifier), Bytes.toBytes("v"));
    }

    @Test
    void leavesOutColumnsTheUserMayNotReadAndDecidesEachOnce()
    {
        List<String> asked = new ArrayList<>();
        ColumnFilter filter = new ColumnFilter((family, qualifier) -> {
            asked.add(family + ":" + qualifier);
            return !"ssn".equals(qualifier);
        });
        assertThat(filter.filterCell(cell("r1", "d", "name"))).isEqualTo(Filter.ReturnCode.INCLUDE);
        assertThat(filter.filterCell(cell("r1", "d", "ssn"))).isEqualTo(Filter.ReturnCode.NEXT_COL);
        assertThat(filter.filterCell(cell("r2", "d", "name"))).isEqualTo(Filter.ReturnCode.INCLUDE);
        assertThat(filter.filterCell(cell("r2", "d", "ssn"))).isEqualTo(Filter.ReturnCode.NEXT_COL);
        assertThat(filter.filterCell(cell("r2", "meta", ""))).isEqualTo(Filter.ReturnCode.INCLUDE);
        assertThat(asked).containsExactly("d:name", "d:ssn", "meta:");
        assertThat(filter).hasToString("GrantForge column filter");
    }
}
