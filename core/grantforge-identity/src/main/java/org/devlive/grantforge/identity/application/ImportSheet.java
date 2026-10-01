// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.lang.Strings;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * The records of an import file read by column name: the first record names the columns (in any case and order;
 * unknown columns are ignored), the others are data rows.
 */
final class ImportSheet
{
    private final Map<String, Integer> columns = new HashMap<>();
    private final List<List<String>> rows;

    /**
     * Reads the records.
     *
     * @param records the parsed file
     * @param required the columns that must be present, in lowercase
     * @param maxRows the most data rows accepted
     * @throws GrantForgeException {@link IdentityErrorCode#IMPORT_EMPTY}, {@link IdentityErrorCode#IMPORT_MISSING_COLUMN}
     *         or {@link IdentityErrorCode#IMPORT_TOO_MANY_ROWS}
     */
    ImportSheet(List<List<String>> records, Set<String> required, int maxRows)
    {
        requireNonNull(records, "records");
        if (records.size() < 2) {
            throw new GrantForgeException(IdentityErrorCode.IMPORT_EMPTY, "no data rows");
        }
        List<String> header = records.get(0);
        for (int i = 0; i < header.size(); i++) {
            columns.putIfAbsent(header.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        required.stream().sorted().filter(column -> !columns.containsKey(column)).findFirst().ifPresent(column -> {
            throw new GrantForgeException(IdentityErrorCode.IMPORT_MISSING_COLUMN, "missing column " + column, column);
        });
        rows = records.subList(1, records.size());
        if (rows.size() > maxRows) {
            throw new GrantForgeException(IdentityErrorCode.IMPORT_TOO_MANY_ROWS, "too many rows", maxRows);
        }
    }

    /**
     * Returns how many data rows the file has.
     *
     * @return the row count
     */
    int size()
    {
        return rows.size();
    }

    /**
     * Returns a trimmed value.
     *
     * @param index the 0-based data row
     * @param column the column, in lowercase
     * @return the value, or {@code null} when blank or the column is absent
     */
    @Nullable String value(int index, String column)
    {
        Integer position = columns.get(column);
        List<String> row = rows.get(index);
        return position == null || position >= row.size() ? null : Strings.blankToNull(row.get(position));
    }

    /**
     * Returns the record number of a data row as people count lines: the header is record 1.
     *
     * @param index the 0-based data row
     * @return the record number
     */
    static int recordOf(int index)
    {
        return index + 2;
    }
}
