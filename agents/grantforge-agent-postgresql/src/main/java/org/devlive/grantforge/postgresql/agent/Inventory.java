// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The schemas of one database, their tables, views and the like, and the tables' columns, with the sequence that fills
 * each serial column. PostgreSQL's own schemas are not in it: policies do not decide them.
 */
final class Inventory
{
    /** An inventory of nothing. */
    static final Inventory EMPTY = new Inventory(Map.of());

    private final SortedMap<String, SortedMap<String, Table>> schemas;

    /**
     * A table, view, materialized view or foreign table.
     *
     * @param ref the table
     * @param kind PostgreSQL's kind of relation: {@code r} a table, {@code p} a partitioned table, {@code v} a view,
     *        {@code m} a materialized view, {@code f} a foreign table
     * @param owner the role that owns it
     * @param columns its columns, in the table's order
     * @param sequences the sequence that fills each serial column, by column
     */
    record Table(TableRef ref, String kind, String owner, List<String> columns, Map<String, TableRef> sequences)
    {
        /** Copies the columns and sequences. */
        Table
        {
            columns = List.copyOf(columns);
            sequences = Map.copyOf(sequences);
        }

        /**
         * Returns whether PostgreSQL filters its rows with row security policies: tables and partitioned tables do,
         * views and the like do not.
         *
         * @return {@code true} for a table
         */
        boolean filterable()
        {
            return "r".equals(kind) || "p".equals(kind);
        }
    }

    /**
     * Holds the inventory.
     *
     * @param schemas the tables of each schema by name, a schema without tables included
     */
    Inventory(Map<String, ? extends Map<String, Table>> schemas)
    {
        SortedMap<String, SortedMap<String, Table>> copy = new TreeMap<>();
        schemas.forEach((schema, tables) -> copy.put(schema, Collections.unmodifiableSortedMap(new TreeMap<>(tables))));
        this.schemas = Collections.unmodifiableSortedMap(copy);
    }

    /**
     * Reads the inventory from rows of schema, name, kind, owner, column and the column's sequence, a row per column; a
     * schema without tables has a row without a name, and a table without columns one without a column.
     *
     * @param rows the rows, in the tables' column order
     * @return the inventory
     */
    // A table and lists per table read.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    static Inventory of(List<Row> rows)
    {
        Map<String, Map<String, Builder>> read = new TreeMap<>();
        for (Row row : rows) {
            Map<String, Builder> tables = read.computeIfAbsent(row.string("schema"), ignored -> new TreeMap<>());
            String name = row.text("name");
            if (name == null) {
                continue;
            }
            Builder table = tables.computeIfAbsent(name, ignored -> new Builder(new TableRef(row.string("schema"), name), row.string("kind"),
                    row.string("owner")));
            String column = row.text("column");
            if (column != null && !table.columns.contains(column)) {
                table.columns.add(column);
            }
            String sequence = row.text("sequence");
            if (column != null && sequence != null) {
                table.sequences.put(column, new TableRef(row.string("sequence_schema"), sequence));
            }
        }
        Map<String, Map<String, Table>> schemas = new TreeMap<>();
        read.forEach((schema, tables) -> {
            Map<String, Table> built = new TreeMap<>();
            tables.forEach((name, table) -> built.put(name, new Table(table.ref, table.kind, table.owner, table.columns, table.sequences)));
            schemas.put(schema, built);
        });
        return new Inventory(schemas);
    }

    private static final class Builder
    {
        private final TableRef ref;
        private final String kind;
        private final String owner;
        private final List<String> columns = new ArrayList<>();
        private final Map<String, TableRef> sequences = new TreeMap<>();

        Builder(TableRef ref, String kind, String owner)
        {
            this.ref = ref;
            this.kind = kind;
            this.owner = owner;
        }
    }

    /**
     * Returns the schemas.
     *
     * @return their names, sorted
     */
    Iterable<String> schemas()
    {
        return schemas.keySet();
    }

    /**
     * Returns a schema's tables.
     *
     * @param schema the schema
     * @return them by name, none for a schema the inventory does not have
     */
    List<Table> tables(String schema)
    {
        SortedMap<String, Table> tables = schemas.get(schema);
        return tables == null ? List.of() : List.copyOf(tables.values());
    }

    /**
     * Returns a table.
     *
     * @param ref the table
     * @return it, or {@code null} for a table the inventory does not have
     */
    @Nullable Table table(TableRef ref)
    {
        SortedMap<String, Table> tables = schemas.get(ref.schema());
        return tables == null ? null : tables.get(ref.table());
    }

    /**
     * Returns a table's columns.
     *
     * @param ref the table
     * @return its columns in the table's order, none for a table the inventory does not have
     */
    List<String> columns(TableRef ref)
    {
        Table table = table(ref);
        return table == null ? List.of() : table.columns();
    }

    /**
     * Returns the tables of a name in any schema, as PostgreSQL's messages name them.
     *
     * @param name the table's name
     * @return the tables, by schema
     */
    List<TableRef> named(String name)
    {
        List<TableRef> found = new ArrayList<>();
        schemas.forEach((schema, tables) -> {
            if (tables.containsKey(name)) {
                found.add(new TableRef(schema, name));
            }
        });
        return found;
    }

    /**
     * Returns the sequences of a name that fill serial columns, in any schema, as PostgreSQL's messages name them.
     *
     * @param name the sequence's name
     * @return the sequences
     */
    List<TableRef> namedSequence(String name)
    {
        List<TableRef> found = new ArrayList<>();
        for (SortedMap<String, Table> tables : schemas.values()) {
            for (Table table : tables.values()) {
                for (TableRef sequence : table.sequences().values()) {
                    if (sequence.table().equals(name) && !found.contains(sequence)) {
                        found.add(sequence);
                    }
                }
            }
        }
        return found;
    }

    /**
     * Returns the table whose serial column a sequence fills.
     *
     * @param sequence the sequence
     * @return the table, or {@code null} when the sequence fills no column the inventory knows of
     */
    @Nullable TableRef filledBy(TableRef sequence)
    {
        for (SortedMap<String, Table> tables : schemas.values()) {
            for (Table table : tables.values()) {
                if (table.sequences().containsValue(sequence)) {
                    return table.ref();
                }
            }
        }
        return null;
    }
}
