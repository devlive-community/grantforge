// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import java.util.List;
import java.util.Map;

/** What the agent asks of ClickHouse: queries with parameters, and statements that change access. */
interface ClickHouse
        extends AutoCloseable
{
    /**
     * Runs a query.
     *
     * @param sql the query, with {@code {name:Type}} parameters
     * @param parameters the parameters' values
     * @return the rows
     * @throws ClickHouseFailure when ClickHouse fails it or cannot be reached
     */
    List<Row> query(String sql, Map<String, Object> parameters);

    /**
     * Runs a statement.
     *
     * @param sql the statement
     * @throws ClickHouseFailure when ClickHouse fails it or cannot be reached
     */
    void execute(String sql);

    @Override
    void close();
}
