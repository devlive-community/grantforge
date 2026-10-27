// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcDorisTest
{
    private static AgentConfig.Connection settings(@Nullable String password, String mode, @Nullable String ca)
    {
        return new AgentConfig.Connection(List.of("fe1:9030", "fe2:9030"), "grantforge", password, mode, ca, Duration.ofMillis(1500));
    }

    /** A connection whose queries return one row of two columns and whose statements run. */
    private static Connection answering() throws SQLException
    {
        Connection connection = mock(Connection.class);
        PreparedStatement prepared = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData columns = mock(ResultSetMetaData.class);
        when(columns.getColumnCount()).thenReturn(2);
        when(columns.getColumnLabel(1)).thenReturn("Name");
        when(columns.getColumnLabel(2)).thenReturn("Value");
        when(rows.getMetaData()).thenReturn(columns);
        when(rows.next()).thenReturn(true, false);
        when(rows.getObject(1)).thenReturn("version_comment");
        when(rows.getObject(2)).thenReturn("doris-4.1.5");
        when(prepared.executeQuery()).thenReturn(rows);
        when(connection.prepareStatement(anyString())).thenReturn(prepared);
        when(connection.createStatement()).thenReturn(mock(Statement.class));
        return connection;
    }

    @Test
    void connectsToTheFrontendsInTheirOrder()
    {
        assertThat(JdbcDoris.url(settings(null, "disable", null))).isEqualTo("jdbc:mariadb:sequential://fe1:9030,fe2:9030/");
        Properties properties = JdbcDoris.properties(settings("secret", "verify-full", "/ca.pem"));
        assertThat(properties).containsEntry("user", "grantforge").containsEntry("password", "secret").containsEntry("sslMode", "verify-full")
                .containsEntry("serverSslCert", "/ca.pem").containsEntry("connectTimeout", "1500").containsEntry("socketTimeout", "1500")
                .containsEntry("allowLocalInfile", "false").containsEntry("allowMultiQueries", "false").containsEntry("retriesAllDown", "2");
        assertThat(JdbcDoris.properties(settings(null, "disable", null))).doesNotContainKeys("password", "serverSslCert");
    }

    @Test
    void readsRowsByColumnInTheirOrderAndBindsParameters() throws SQLException
    {
        Connection connection = answering();
        JdbcDoris doris = new JdbcDoris(settings(null, "disable", null), (url, properties) -> connection);
        List<Row> rows = doris.query("SHOW VARIABLES WHERE x = ? AND y = ?", "a", 2);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.first()).isEqualTo("version_comment");
            assertThat(row.string("Value")).isEqualTo("doris-4.1.5");
        });
        PreparedStatement statement = connection.prepareStatement("");
        verify(statement).setObject(1, "a");
        verify(statement).setObject(2, 2);
        doris.execute("CREATE ROLE `r`");
        verify(connection.createStatement()).execute("CREATE ROLE `r`");
        doris.close();
        verify(connection).close();
    }

    @Test
    void aFirstConnectionThatBreaksIsNotTriedAgain() throws SQLException
    {
        Connection broken = mock(Connection.class);
        when(broken.prepareStatement(anyString())).thenThrow(new SQLNonTransientConnectionException("gone", "08000"));
        JdbcDoris doris = new JdbcDoris(settings(null, "disable", null), (url, properties) -> broken);
        assertThatThrownBy(() -> doris.query("SHOW ROLES")).isInstanceOfSatisfying(DorisFailure.class,
                failure -> assertThat(failure.unreachable()).isTrue());
        verify(broken).close();
    }

    @Test
    void opensAnOpenConnectionThatBrokeAgainOnce() throws SQLException
    {
        Connection first = answering();
        Connection second = answering();
        AtomicInteger opened = new AtomicInteger();
        JdbcDoris doris = new JdbcDoris(settings(null, "disable", null), (url, properties) -> opened.getAndIncrement() == 0 ? first : second);
        assertThat(doris.query("SHOW ROLES")).hasSize(1);
        when(first.prepareStatement(anyString())).thenThrow(new SQLNonTransientConnectionException("gone"));
        assertThat(doris.query("SHOW ROLES")).hasSize(1);
        verify(first).close();
        assertThat(opened).hasValue(2);

        when(second.prepareStatement(anyString())).thenThrow(new SQLNonTransientConnectionException("gone"));
        Connection third = mock(Connection.class);
        when(third.prepareStatement(anyString())).thenThrow(new SQLNonTransientConnectionException("still gone"));
        JdbcDoris failing = new JdbcDoris(settings(null, "disable", null), (url, properties) -> opened.getAndIncrement() == 2 ? second : third);
        assertThatThrownBy(() -> failing.query("SHOW ROLES")).isInstanceOf(DorisFailure.class);
        verify(third, times(0)).close();
    }

    @Test
    void refusalsAreNoConnectionFailures() throws SQLException
    {
        Connection connection = mock(Connection.class);
        when(connection.createStatement()).thenThrow(new SQLSyntaxErrorException("(conn=7) errCode = 2, detailMessage = Syntax error\nat line 1",
                "42000", 1064));
        JdbcDoris doris = new JdbcDoris(settings(null, "disable", null), (url, properties) -> connection);
        assertThatThrownBy(() -> doris.execute("GRANT nonsense")).isInstanceOfSatisfying(DorisFailure.class, failure -> {
            assertThat(failure.unreachable()).isFalse();
            assertThat(failure).hasMessage("Syntax error");
        });
    }

    @Test
    void saysWhenTheDriverTakesNoUrl()
    {
        JdbcDoris doris = new JdbcDoris(settings(null, "disable", null), (url, properties) -> null);
        assertThatThrownBy(() -> doris.query("SHOW ROLES")).isInstanceOfSatisfying(DorisFailure.class,
                failure -> assertThat(failure.unreachable()).isTrue());
    }

    @Test
    void keepsTheUsefulPartOfAMessage()
    {
        assertThat(JdbcDoris.message(new SQLException("(conn=12) errCode = 2, detailMessage = Access denied; you need it\nmore")))
                .isEqualTo("Access denied; you need it");
        assertThat(JdbcDoris.message(new SQLException((String) null))).isEqualTo("SQLException");
        assertThat(JdbcDoris.failure(new SQLException("timeout", "08S01")).unreachable()).isTrue();
    }
}
