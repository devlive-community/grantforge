// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.perf;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Prepares planner statistics and indexes for the data just bulk loaded by the benchmark. */
final class DatabaseStatistics
{
    private DatabaseStatistics()
    {
    }

    /**
     * Vacuums and analyzes PostgreSQL after seeding, before database-backed measurements. Otherwise the plan depends on when
     * autovacuum updates table statistics and cleans GIN pending lists, and a selective search may scan a million accounts.
     *
     * @param source the benchmark server's data source
     * @return whether PostgreSQL tables and indexes were prepared
     * @throws SQLException if obtaining the connection or preparing the database fails
     */
    static boolean prepare(DataSource source) throws SQLException
    {
        try (Connection connection = source.getConnection()) {
            if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                return false;
            }
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute("VACUUM (ANALYZE)");
            }
            return true;
        }
    }
}
