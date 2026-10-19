// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.perf;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseStatisticsTest
{
    private final DataSource source = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final DatabaseMetaData metadata = mock(DatabaseMetaData.class);
    private final Statement statement = mock(Statement.class);

    private void database(String name) throws SQLException
    {
        when(source.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn(name);
    }

    @Test
    void preparesPostgresOutsideATransactionAndClosesTheConnection() throws SQLException
    {
        database("PostgreSQL");
        when(connection.createStatement()).thenReturn(statement);

        assertThat(DatabaseStatistics.prepare(source)).isTrue();

        var ordered = inOrder(connection, statement);
        ordered.verify(connection).setAutoCommit(true);
        ordered.verify(connection).createStatement();
        ordered.verify(statement).execute("VACUUM (ANALYZE)");
        ordered.verify(statement).close();
        ordered.verify(connection).close();
    }

    @Test
    void leavesOtherDatabasesUnchanged() throws SQLException
    {
        database("H2");

        assertThat(DatabaseStatistics.prepare(source)).isFalse();

        verify(connection, never()).createStatement();
        verify(connection, never()).setAutoCommit(true);
        verify(connection).close();
    }

    @Test
    void failsWhenPreparationFailsAndStillClosesResources() throws SQLException
    {
        database("PostgreSQL");
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute("VACUUM (ANALYZE)")).thenThrow(new SQLException("vacuum failed"));

        assertThatThrownBy(() -> DatabaseStatistics.prepare(source)).isInstanceOf(SQLException.class)
                .hasMessage("vacuum failed");

        verify(statement).close();
        verify(connection).close();
    }
}
