// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * What one statement needs, as {@link SqlAnalyzer} finds it: the accesses to check, the tables it reads (where masks and
 * row filters go), the tables it writes and the columns it uses of each table. A statement that uses no data, such as
 * BEGIN or SHOW, has no accesses: MySQL's own grants decide what it may do.
 */
public final class StatementPlan
{
    private final SQLStatement statement;
    private final String action;
    private final Set<Access> accesses = new LinkedHashSet<>();
    private final List<Read> reads = new ArrayList<>();
    private final Set<TableName> targets = new LinkedHashSet<>();
    private final Map<TableName, Set<String>> used = new LinkedHashMap<>();
    private boolean view;
    private boolean explain;
    private @Nullable TableName filtered;
    private @Nullable String database;
    private @Nullable String charset;

    StatementPlan(SQLStatement statement, String action)
    {
        this.statement = requireNonNull(statement, "statement");
        this.action = requireNonNull(action, "action");
    }

    /**
     * A read of a base table that a mask or row filter may replace with a derived table.
     *
     * @param source where the statement names the table
     * @param table the table
     */
    public record Read(SQLExprTableSource source, TableName table)
    {
    }

    /**
     * Returns the statement, which a rewrite changes in place.
     *
     * @return the statement
     */
    public SQLStatement statement()
    {
        return statement;
    }

    /**
     * Returns what the statement does, for audit events.
     *
     * @return such as {@code SELECT} or {@code ALTER TABLE}
     */
    public String action()
    {
        return action;
    }

    /**
     * Returns the accesses to check, in the order found.
     *
     * @return the accesses
     */
    public Set<Access> accesses()
    {
        return Collections.unmodifiableSet(accesses);
    }

    /**
     * Returns the reads of base tables.
     *
     * @return the reads
     */
    public List<Read> reads()
    {
        return Collections.unmodifiableList(reads);
    }

    /**
     * Returns the tables UPDATE or DELETE changes, whose rows a filter limits and whose masked columns they may not use.
     *
     * @return the tables
     */
    public Set<TableName> targets()
    {
        return Collections.unmodifiableSet(targets);
    }

    /**
     * Returns the columns the statement uses of a table, anywhere in it.
     *
     * @param table the table
     * @return the columns as the catalog names them
     */
    public Set<String> used(TableName table)
    {
        Set<String> columns = used.get(table);
        return columns == null ? Collections.emptySet() : Collections.unmodifiableSet(columns);
    }

    /**
     * Returns whether the statement defines a view, which MySQL runs later as its definer: masks and row filters
     * cannot follow its reads, so it may not read what they would change.
     *
     * @return {@code true} for CREATE VIEW and ALTER VIEW
     */
    public boolean view()
    {
        return view;
    }

    /**
     * Returns whether the statement only explains another, which is checked but not rewritten.
     *
     * @return {@code true} for EXPLAIN
     */
    public boolean explain()
    {
        return explain;
    }

    /**
     * Returns the one table a single-table UPDATE or DELETE changes, whose row filter joins its WHERE.
     *
     * @return the table, or {@code null}
     */
    public @Nullable TableName filtered()
    {
        return filtered;
    }

    /**
     * Returns the database USE selects.
     *
     * @return it, or {@code null} for other statements
     */
    public @Nullable String database()
    {
        return database;
    }

    /**
     * Returns the character set SET NAMES or SET character_set_client selects for the client's statements.
     *
     * @return its MySQL name, or {@code null} for other statements
     */
    public @Nullable String charset()
    {
        return charset;
    }

    void access(Access access)
    {
        accesses.add(access);
    }

    void read(Read read)
    {
        reads.add(read);
    }

    void target(TableName table)
    {
        targets.add(table);
    }

    void use(TableName table, String column)
    {
        used.computeIfAbsent(table, key -> new LinkedHashSet<>()).add(column);
    }

    void view(boolean value)
    {
        view = value;
    }

    void explain(boolean value)
    {
        explain = value;
    }

    void filtered(@Nullable TableName value)
    {
        filtered = value;
    }

    void database(@Nullable String value)
    {
        database = value;
    }

    void charset(@Nullable String value)
    {
        charset = value;
    }
}
