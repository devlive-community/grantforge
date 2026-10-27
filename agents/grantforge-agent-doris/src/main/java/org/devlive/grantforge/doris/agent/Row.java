// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.jspecify.annotations.Nullable;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One row of a query, by column name in the query's order, read as the agent's queries shape them: text, whole numbers
 * and moments.
 */
final class Row
{
    private final Map<String, @Nullable Object> values;

    Row(Map<String, ? extends @Nullable Object> values)
    {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<String, @Nullable Object>(values));
    }

    /**
     * Builds a row from column, value pairs.
     *
     * @param pairs the columns' names and values
     * @return the row
     */
    static Row of(@Nullable Object... pairs)
    {
        Map<String, @Nullable Object> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return new Row(values);
    }

    /**
     * Reads text.
     *
     * @param column the column
     * @return the value, or {@code null} for NULL
     */
    @Nullable String text(String column)
    {
        Object value = values.get(column);
        return value == null ? null : value.toString();
    }

    /**
     * Reads the first column as text, as {@code SHOW} statements give a name there.
     *
     * @return the value, empty for NULL or a row without columns
     */
    String first()
    {
        Iterator<@Nullable Object> all = values.values().iterator();
        Object value = all.hasNext() ? all.next() : null;
        return value == null ? "" : value.toString();
    }

    /**
     * Reads text that is never NULL.
     *
     * @param column the column
     * @return the value, empty for NULL
     */
    String string(String column)
    {
        String value = text(column);
        return value == null ? "" : value;
    }

    /**
     * Reads a whole number.
     *
     * @param column the column
     * @return the value, 0 for NULL
     */
    long number(String column)
    {
        Object value = values.get(column);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof Boolean flag) {
            return flag ? 1 : 0;
        }
        return value == null ? 0 : Long.parseLong(value.toString());
    }

    /**
     * Reads a flag.
     *
     * @param column the column
     * @return whether it is set
     */
    boolean flag(String column)
    {
        return number(column) != 0;
    }

    /**
     * Reads a moment.
     *
     * @param column the column
     * @return the value, or {@code null} for NULL
     */
    @Nullable LocalDateTime moment(String column)
    {
        Object value = values.get(column);
        if (value instanceof LocalDateTime moment) {
            return moment;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return value == null ? null : LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }

    @Override
    public String toString()
    {
        return values.toString();
    }
}
