// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase.agent;

import org.apache.hadoop.hbase.TableName;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Something a request touches, named at each scope of HBase's permissions from the top: the global scope alone, or a
 * namespace, a table within it, a column family and a column. In HBase a permission on a scope covers everything within
 * it, so a request is decided at each scope it names; see {@link Policies}.
 */
final class HBaseResource
{
    static final String GLOBAL = "global";
    static final String NAMESPACE = "namespace";
    static final String TABLE = "table";
    static final String COLUMN_FAMILY = "column_family";
    static final String COLUMN = "column";

    private static final String ANY = "*";
    private static final List<String> LEVELS = Collections.unmodifiableList(java.util.Arrays.asList(NAMESPACE, TABLE, COLUMN_FAMILY, COLUMN));

    private final boolean global;
    private final List<String> values;

    private HBaseResource(boolean global, List<String> values)
    {
        this.global = global;
        this.values = Collections.unmodifiableList(new ArrayList<>(values));
    }

    /**
     * Returns the global scope, of operations on the whole cluster.
     *
     * @return it
     */
    static HBaseResource global()
    {
        return new HBaseResource(true, Collections.singletonList(ANY));
    }

    /**
     * Returns a namespace.
     *
     * @param name its name
     * @return it
     */
    static HBaseResource namespace(String name)
    {
        return new HBaseResource(false, Collections.singletonList(name));
    }

    /**
     * Returns a table in its namespace.
     *
     * @param table the table
     * @return it
     */
    static HBaseResource table(TableName table)
    {
        return new HBaseResource(false, java.util.Arrays.asList(table.getNamespaceAsString(), table.getQualifierAsString()));
    }

    /**
     * Returns a column family of this table.
     *
     * @param family its name
     * @return it
     * @throws IllegalStateException unless this is a table
     */
    HBaseResource family(String family)
    {
        return below(2, family);
    }

    /**
     * Returns a column of this column family.
     *
     * @param qualifier its qualifier
     * @return it
     * @throws IllegalStateException unless this is a column family
     */
    HBaseResource column(String qualifier)
    {
        return below(3, qualifier);
    }

    private HBaseResource below(int depth, String value)
    {
        if (global || values.size() != depth) {
            throw new IllegalStateException("nothing of the " + level() + " level is below " + display());
        }
        List<String> deeper = new ArrayList<>(values);
        deeper.add(value);
        return new HBaseResource(false, deeper);
    }

    /**
     * Returns the resource at each scope it names, from the top: for a column, its namespace, its table, its column family
     * and the column itself.
     *
     * @return the levels and values of each scope
     */
    List<Map<String, String>> scopes()
    {
        List<Map<String, String>> scopes = new ArrayList<>();
        if (global) {
            scopes.add(Collections.singletonMap(GLOBAL, ANY));
            return scopes;
        }
        Map<String, String> scope = new LinkedHashMap<>();
        for (int index = 0; index < values.size(); index++) {
            scope.put(LEVELS.get(index), values.get(index));
            scopes.add(Collections.unmodifiableMap(new LinkedHashMap<>(scope)));
        }
        return scopes;
    }

    /**
     * Returns the deepest level the resource names.
     *
     * @return such as {@code table}
     */
    String level()
    {
        return global ? GLOBAL : LEVELS.get(values.size() - 1);
    }

    /**
     * Returns the values, from the top.
     *
     * @return them
     */
    List<String> values()
    {
        return values;
    }

    /**
     * Returns the namespace.
     *
     * @return it, or {@code null} for the global scope
     */
    @Nullable String namespace()
    {
        return global ? null : values.get(0);
    }

    /**
     * Returns the resource as HBase names it: {@code *} for the global scope, {@code namespace},
     * {@code namespace:table}, {@code namespace:table/family} or {@code namespace:table/family:column}.
     *
     * @return the name
     */
    String display()
    {
        if (global) {
            return ANY;
        }
        StringBuilder name = new StringBuilder(values.get(0));
        if (values.size() > 1) {
            name.append(':').append(values.get(1));
        }
        if (values.size() > 2) {
            name.append('/').append(values.get(2));
        }
        if (values.size() > 3) {
            name.append(':').append(values.get(3));
        }
        return name.toString();
    }

    @Override
    public boolean equals(@Nullable Object other)
    {
        return other instanceof HBaseResource && ((HBaseResource) other).global == global && ((HBaseResource) other).values.equals(values);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(global, values);
    }

    @Override
    public String toString()
    {
        return display();
    }
}
