// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One row of a query, by column name, read as the agent's queries shape them: text, whole numbers, flags and arrays of
 * text.
 */
final class Row
{
    private final Map<String, @Nullable Object> values;

    Row(Map<String, ? extends @Nullable Object> values)
    {
        this.values = Collections.unmodifiableMap(new HashMap<String, @Nullable Object>(values));
    }

    /**
     * Builds a row from column, value pairs.
     *
     * @param pairs the columns' names and values
     * @return the row
     */
    static Row of(@Nullable Object... pairs)
    {
        Map<String, @Nullable Object> values = new HashMap<>();
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
     * Reads an array of text.
     *
     * @param column the column
     * @return the values, none for NULL
     */
    List<String> texts(String column)
    {
        Object value = values.get(column);
        List<?> items;
        if (value instanceof List<?> list) {
            items = list;
        }
        else if (value instanceof Object[] array) {
            items = Arrays.asList(array);
        }
        else {
            items = List.of();
        }
        List<String> texts = new ArrayList<>(items.size());
        for (Object item : items) {
            texts.add(String.valueOf(item));
        }
        return texts;
    }

    /**
     * Reads bytes.
     *
     * @param column the column
     * @return the value, none for NULL
     */
    byte[] bytes(String column)
    {
        Object value = values.get(column);
        return value instanceof byte[] bytes ? bytes.clone() : new byte[0];
    }

    @Override
    public String toString()
    {
        return values.toString();
    }
}
