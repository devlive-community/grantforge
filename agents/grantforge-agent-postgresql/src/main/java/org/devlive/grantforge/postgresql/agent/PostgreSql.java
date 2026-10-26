// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What the agent asks of PostgreSQL, one database at a time: queries with parameters, and changes made in one
 * transaction, so that users see all of a database's changes or none of them.
 */
interface PostgreSql
        extends AutoCloseable
{
    /**
     * Runs a query.
     *
     * @param database the database
     * @param sql the query, with {@code ?} parameters
     * @param parameters the parameters' values: text, numbers, flags or lists of text
     * @return the rows
     * @throws PostgreSqlFailure when PostgreSQL fails it or cannot be reached
     */
    List<Row> query(String database, String sql, Object... parameters);

    /**
     * Runs a statement on its own.
     *
     * @param database the database
     * @param sql the statement
     * @throws PostgreSqlFailure when PostgreSQL fails it or cannot be reached
     */
    void execute(String database, String sql);

    /**
     * Makes changes in one transaction, which commits when the work returns and rolls back when it throws.
     *
     * @param database the database
     * @param work the changes
     * @throws PostgreSqlFailure when PostgreSQL fails a statement or the commit, or cannot be reached
     */
    void transaction(String database, Work work);

    @Override
    void close();

    /** Changes made in a transaction. */
    @FunctionalInterface
    interface Work
    {
        /**
         * Makes the changes.
         *
         * @param session the transaction
         */
        void run(Session session);
    }

    /** A transaction in progress. */
    interface Session
    {
        /**
         * Runs a statement.
         *
         * @param sql the statement
         * @throws PostgreSqlFailure when PostgreSQL fails it; the transaction then rolls back
         */
        void execute(String sql);

        /**
         * Runs a statement that may fail without failing the transaction: what it did is undone when it fails.
         *
         * @param sql the statement
         * @return why it failed, or {@code null} when it succeeded
         * @throws PostgreSqlFailure when the server cannot be reached
         */
        @Nullable PostgreSqlFailure attempt(String sql);

        /**
         * Runs a query in the transaction, which sees its changes.
         *
         * @param sql the query, with {@code ?} parameters
         * @param parameters the parameters' values
         * @return the rows
         * @throws PostgreSqlFailure when PostgreSQL fails it
         */
        List<Row> query(String sql, Object... parameters);
    }
}
