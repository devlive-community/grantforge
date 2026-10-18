// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase.agent;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.filter.FilterBase;
import org.apache.hadoop.hbase.util.Bytes;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Leaves out of a read the cells of columns the user may not read, so a read returns the columns the user may read rather
 * than failing. It runs in the region server, set on the read by the agent, and is never sent anywhere. Each column is
 * decided once per read.
 */
final class ColumnFilter
        extends FilterBase
{
    private final BiPredicate<String, String> readable;
    private final Map<String, Boolean> columns = new HashMap<>();

    /**
     * Creates the filter.
     *
     * @param readable whether a column, by family and qualifier, may be read
     */
    ColumnFilter(BiPredicate<String, String> readable)
    {
        this.readable = readable;
    }

    @Override
    public ReturnCode filterCell(Cell cell)
    {
        String family = Bytes.toString(cell.getFamilyArray(), cell.getFamilyOffset(), cell.getFamilyLength());
        String qualifier = Bytes.toString(cell.getQualifierArray(), cell.getQualifierOffset(), cell.getQualifierLength());
        Boolean allowed = columns.computeIfAbsent(family + '\u0000' + qualifier, ignored -> readable.test(family, qualifier));
        return allowed ? ReturnCode.INCLUDE : ReturnCode.NEXT_COL;
    }

    @Override
    public String toString()
    {
        return "GrantForge column filter";
    }
}
