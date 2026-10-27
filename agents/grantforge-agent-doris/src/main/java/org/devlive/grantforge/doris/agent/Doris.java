// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import java.util.List;

/** What the agent asks of a Doris cluster: queries with parameters and statements, one at a time. */
interface Doris
        extends AutoCloseable
{
    /**
     * Runs a query.
     *
     * @param sql the query, with {@code ?} parameters
     * @param parameters the parameters' values
     * @return the rows, by column label
     * @throws DorisFailure when Doris fails it or no frontend can be reached
     */
    List<Row> query(String sql, Object... parameters);

    /**
     * Runs a statement.
     *
     * @param sql the statement
     * @throws DorisFailure when Doris refuses or fails it, or no frontend can be reached
     */
    void execute(String sql);

    @Override
    void close();
}
