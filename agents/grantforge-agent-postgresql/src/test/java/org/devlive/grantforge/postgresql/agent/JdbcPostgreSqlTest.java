// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcPostgreSqlTest
{
    private static AgentConfig.Connection settings(String mode, @Nullable String ca, @Nullable String certificate)
    {
        return new AgentConfig.Connection(List.of("pg1:5432", "pg2:5432"), "postgres", "grantforge", certificate == null ? "secret" : null, mode, ca,
                certificate, certificate == null ? null : "/k.pem", Duration.ofMillis(1500));
    }

    private static AgentConfig.Connection plain()
    {
        return settings("prefer", null, null);
    }

    /** A connection whose queries return one row with the given column. */
    private static Connection answering(String column, Object value) throws SQLException
    {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData columns = mock(ResultSetMetaData.class);
        when(columns.getColumnCount()).thenReturn(1);
        when(columns.getColumnLabel(1)).thenReturn(column);
        when(rows.getMetaData()).thenReturn(columns);
        when(rows.next()).thenReturn(true, false);
        when(rows.getObject(1)).thenReturn(value);
        when(statement.executeQuery()).thenReturn(rows);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(connection.setSavepoint()).thenReturn(mock(Savepoint.class));
        return connection;
    }

    @Test
    void connectsToOneDatabaseOfOneServerAtATime()
    {
        assertThat(JdbcPostgreSql.url("pg1:5432", "my db/1")).isEqualTo("jdbc:postgresql://pg1:5432/my%20db%2F1");
        Properties properties = JdbcPostgreSql.properties(plain());
        assertThat(properties).containsEntry("user", "grantforge").containsEntry("password", "secret").containsEntry("sslcert", "")
                .containsEntry("sslmode", "prefer").containsEntry("connectTimeout", "2").containsEntry("socketTimeout", "2")
                .containsEntry("loginTimeout", "2").containsEntry("targetServerType", "primary").doesNotContainKeys("sslkey", "sslrootcert", "sslfactory");
        assertThat(JdbcPostgreSql.properties(settings("verify-full", "/ca.pem", "/c.pem"))).containsEntry("sslrootcert", "/ca.pem")
                .containsEntry("sslcert", "/c.pem").containsEntry("sslkey", "/k.pem").doesNotContainKey("password");
        assertThat(JdbcPostgreSql.properties(settings("verify-ca", null, null))).containsEntry("sslfactory", JdbcPostgreSql.JAVA_TRUST);
    }

    @Test
    void readsRowsBindingTextNumbersFlagsAndLists() throws SQLException
    {
        Array array = mock(Array.class);
        when(array.getArray()).thenReturn(new String[] {"a", "b"});
        Connection connection = answering("roles", array);
        when(connection.createArrayOf(eq("text"), any())).thenReturn(array);
        JdbcPostgreSql postgres = new JdbcPostgreSql(plain(), (url, properties) -> connection);
        List<Row> rows = postgres.query("sales", "SELECT ? ? ? ? ?", "x", 1L, 2, true, List.of("y"));
        assertThat(rows).singleElement().satisfies(row -> assertThat(row.texts("roles")).containsExactly("a", "b"));
        PreparedStatement statement = connection.prepareStatement("");
        verify(statement).setString(1, "x");
        verify(statement).setLong(2, 1L);
        verify(statement).setLong(3, 2L);
        verify(statement).setBoolean(4, true);
        verify(statement).setArray(5, array);

        Array odd = mock(Array.class);
        when(odd.getArray()).thenReturn(new int[] {1});
        assertThat(new JdbcPostgreSql(plain(), (url, properties) -> answering("x", odd)).query("sales", "SELECT 1").get(0).texts("x")).isEmpty();
        Object other = new Object()
        {
            @Override
            public String toString()
            {
                return "(1,2)";
            }
        };
        assertThat(new JdbcPostgreSql(plain(), (url, properties) -> answering("x", other)).query("sales", "SELECT 1").get(0).text("x")).isEqualTo("(1,2)");
        assertThat(new JdbcPostgreSql(plain(), (url, properties) -> answering("x", new byte[] {7})).query("sales", "SELECT 1").get(0).bytes("x"))
                .containsExactly(7);
    }

    @Test
    void goesToTheNextServerOnlyWhenOneCannotBeReachedAndKeepsTheConnection() throws SQLException
    {
        Connection connection = answering("x", "1");
        List<String> tried = new ArrayList<>();
        JdbcPostgreSql postgres = new JdbcPostgreSql(plain(), (url, properties) -> {
            tried.add(url);
            if (url.contains("pg1")) {
                throw new SQLException("Connection refused", "08001");
            }
            return connection;
        });
        postgres.query("sales", "SELECT 1");
        postgres.execute("sales", "GRANT x TO y");
        assertThat(tried).containsExactly("jdbc:postgresql://pg1:5432/sales", "jdbc:postgresql://pg2:5432/sales");
        postgres.close();
        verify(connection).close();

        JdbcPostgreSql refusing = new JdbcPostgreSql(plain(), (url, properties) -> {
            throw new SQLException("password authentication failed", "28P01");
        });
        assertThatThrownBy(() -> refusing.query("sales", "SELECT 1")).isInstanceOfSatisfying(PostgreSqlFailure.class,
                failed -> assertThat(failed.state()).isEqualTo("28P01")).hasMessageStartingWith("pg1:5432/sales");
        JdbcPostgreSql down = new JdbcPostgreSql(plain(), (url, properties) -> {
            throw new SQLException("Connection refused", "08001");
        });
        assertThatThrownBy(() -> down.query("sales", "SELECT 1")).isInstanceOfSatisfying(PostgreSqlFailure.class,
                failed -> assertThat(failed.unreachable()).isTrue()).hasMessageStartingWith("pg1:5432/sales");
        JdbcPostgreSql foreign = new JdbcPostgreSql(plain(), (url, properties) -> null);
        assertThatThrownBy(() -> foreign.query("sales", "SELECT 1")).isInstanceOf(PostgreSqlFailure.class).hasMessageContaining("does not accept");
        JdbcPostgreSql none = new JdbcPostgreSql(new AgentConfig.Connection(List.of(), "postgres", "u", null, "prefer", null, null, null,
                Duration.ofSeconds(1)), (url, properties) -> connection);
        assertThatThrownBy(() -> none.query("sales", "SELECT 1")).isInstanceOf(PostgreSqlFailure.class).hasMessageContaining("no PostgreSQL server");
    }

    @Test
    void opensABrokenConnectionAgainOnce() throws SQLException
    {
        Connection broken = answering("x", "1");
        PreparedStatement working = broken.prepareStatement("x");
        when(broken.prepareStatement(anyString())).thenReturn(working).thenThrow(new SQLException("An I/O error occurred", "08006"));
        doThrow(new SQLException("already closed")).when(broken).close();
        Connection fresh = answering("x", "2");
        List<Connection> connections = new ArrayList<>(List.of(broken, fresh));
        JdbcPostgreSql postgres = new JdbcPostgreSql(plain(), (url, properties) -> connections.remove(0));
        postgres.execute("sales", "SELECT 0");
        assertThat(postgres.query("sales", "SELECT 1").get(0).text("x")).isEqualTo("2");

        Connection failing = answering("x", "1");
        when(failing.prepareStatement(anyString())).thenThrow(new SQLException("relation does not exist", "42P01"));
        JdbcPostgreSql refusing = new JdbcPostgreSql(plain(), (url, properties) -> failing);
        assertThatThrownBy(() -> refusing.query("sales", "SELECT 1")).isInstanceOfSatisfying(PostgreSqlFailure.class,
                failed -> assertThat(failed.unreachable()).isFalse());

        JdbcPostgreSql first = new JdbcPostgreSql(plain(), (url, properties) -> broken);
        assertThatThrownBy(() -> first.query("sales", "SELECT 1")).as("a new connection that breaks at once is not tried again")
                .isInstanceOf(PostgreSqlFailure.class);

        Connection again = answering("x", "1");
        PreparedStatement once = again.prepareStatement("x");
        when(again.prepareStatement(anyString())).thenReturn(once).thenThrow(new SQLException("gone", "08006"));
        JdbcPostgreSql twice = new JdbcPostgreSql(plain(), (url, properties) -> again);
        twice.execute("sales", "SELECT 0");
        assertThatThrownBy(() -> twice.query("sales", "SELECT 1")).isInstanceOfSatisfying(PostgreSqlFailure.class,
                failed -> assertThat(failed.unreachable()).isTrue());
    }

    @Test
    void refusesTextsOfSeveralStatementsBeforeSendingThem() throws SQLException
    {
        Connection connection = answering("x", "1");
        JdbcPostgreSql postgres = new JdbcPostgreSql(plain(), (url, properties) -> connection);
        assertThatThrownBy(() -> postgres.execute("sales", "SELECT 1; DROP TABLE t")).isInstanceOfSatisfying(PostgreSqlFailure.class,
                failed -> assertThat(failed.state()).isEqualTo("42601"));
        verify(connection, never()).prepareStatement(anyString());
    }

    @Test
    void commitsATransactionOrRollsItBack() throws SQLException
    {
        Connection connection = answering("x", "1");
        JdbcPostgreSql postgres = new JdbcPostgreSql(plain(), (url, properties) -> connection);
        postgres.transaction("sales", session -> {
            session.execute("GRANT a TO b");
            assertThat(session.query("SELECT ?", "x")).hasSize(1);
        });
        verify(connection).setAutoCommit(false);
        verify(connection).commit();
        verify(connection).setAutoCommit(true);

        assertThatThrownBy(() -> postgres.transaction("sales", session -> {
            throw new IllegalStateException("broken");
        })).isInstanceOf(IllegalStateException.class);
        verify(connection).rollback();

        PreparedStatement failing = mock(PreparedStatement.class);
        when(failing.execute()).thenThrow(new SQLException("permission denied", "42501"));
        when(failing.executeQuery()).thenThrow(new SQLException("permission denied", "42501"));
        when(connection.prepareStatement("REVOKE x")).thenReturn(failing);
        doThrow(new SQLException("no transaction")).when(connection).rollback();
        assertThatThrownBy(() -> postgres.transaction("sales", session -> session.execute("REVOKE x"))).isInstanceOfSatisfying(
                PostgreSqlFailure.class, failed -> assertThat(failed.state()).isEqualTo("42501"));
        assertThatThrownBy(() -> postgres.transaction("sales", session -> session.query("REVOKE x"))).isInstanceOf(PostgreSqlFailure.class);

        doThrow(new SQLException("gone", "08006")).when(connection).commit();
        assertThatThrownBy(() -> postgres.transaction("sales", session -> session.execute("GRANT a TO b"))).isInstanceOfSatisfying(
                PostgreSqlFailure.class, failed -> assertThat(failed.unreachable()).isTrue());
        verify(connection).close();

        Connection lost = answering("x", "1");
        doThrow(new SQLException("gone", "08006")).when(lost).setAutoCommit(false);
        JdbcPostgreSql losing = new JdbcPostgreSql(plain(), (url, properties) -> lost);
        assertThatThrownBy(() -> losing.transaction("sales", session -> session.execute("GRANT a TO b"))).isInstanceOfSatisfying(
                PostgreSqlFailure.class, failed -> assertThat(failed.unreachable()).isTrue());
    }

    @Test
    void attemptsAStatementUnderASavepoint() throws SQLException
    {
        Connection connection = answering("x", "1");
        PreparedStatement failing = mock(PreparedStatement.class);
        when(failing.execute()).thenThrow(new SQLException("syntax error", "42601"));
        when(connection.prepareStatement("CREATE POLICY bad")).thenReturn(failing);
        PreparedStatement broken = mock(PreparedStatement.class);
        when(broken.execute()).thenThrow(new SQLException("gone", "08006"));
        when(connection.prepareStatement("CREATE POLICY lost")).thenReturn(broken);
        JdbcPostgreSql postgres = new JdbcPostgreSql(plain(), (url, properties) -> connection);
        List<PostgreSqlFailure> results = new ArrayList<>();
        postgres.transaction("sales", session -> {
            assertThat(session.attempt("CREATE POLICY good")).isNull();
            PostgreSqlFailure refused = session.attempt("CREATE POLICY bad");
            assertThat(refused).isNotNull();
            results.add(refused);
        });
        assertThat(results).singleElement().satisfies(failed -> assertThat(failed.state()).isEqualTo("42601"));
        verify(connection).releaseSavepoint(any());
        verify(connection).rollback(any(Savepoint.class));
        assertThatThrownBy(() -> postgres.transaction("sales", session -> session.attempt("CREATE POLICY lost"))).isInstanceOf(PostgreSqlFailure.class);

        Connection noSavepoints = answering("x", "1");
        when(noSavepoints.setSavepoint()).thenThrow(new SQLException("gone", "08006"));
        JdbcPostgreSql without = new JdbcPostgreSql(plain(), (url, properties) -> noSavepoints);
        assertThatThrownBy(() -> without.transaction("sales", session -> session.attempt("CREATE POLICY good"))).isInstanceOf(PostgreSqlFailure.class);

        Connection noRollback = answering("x", "1");
        when(noRollback.prepareStatement("CREATE POLICY bad")).thenReturn(failing);
        doThrow(new SQLException("gone", "08006")).when(noRollback).rollback(any(Savepoint.class));
        JdbcPostgreSql stuck = new JdbcPostgreSql(plain(), (url, properties) -> noRollback);
        assertThatThrownBy(() -> stuck.transaction("sales", session -> session.attempt("CREATE POLICY bad"))).isInstanceOf(PostgreSqlFailure.class);
        verify(noRollback, times(1)).rollback(any(Savepoint.class));
    }

    @Test
    void namesTheFirstLineOfAFailure()
    {
        assertThat(JdbcPostgreSql.message(new SQLException("first\nsecond"))).isEqualTo("first");
        assertThat(JdbcPostgreSql.message(new SQLException())).isEqualTo("SQLException");
    }
}
