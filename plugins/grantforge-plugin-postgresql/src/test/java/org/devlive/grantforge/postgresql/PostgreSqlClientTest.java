// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql;

import org.devlive.grantforge.plugin.api.LookupException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PostgreSqlClientTest
{
    private static PostgreSqlSettings settings(String... values)
    {
        return PostgreSqlSettings.of(PostgreSqlSettingsTest.config(values));
    }

    @Test
    void namesTheDatabaseOfOneServer()
    {
        assertThat(PostgreSqlClient.url("pg1:5432", "sales")).isEqualTo("jdbc:postgresql://pg1:5432/sales");
        assertThat(PostgreSqlClient.url("[::1]:5432", "my db/1")).isEqualTo("jdbc:postgresql://[::1]:5432/my%20db%2F1");
    }

    @Test
    void signsInWithAPasswordAndShowsNoCertificateOfItsOwn()
    {
        Properties properties = PostgreSqlClient.properties(settings(PostgreSqlSettings.PASSWORD, "secret", PostgreSqlSettings.TIMEOUT, "1500"));
        assertThat(properties).containsEntry("user", "lookup").containsEntry("password", "secret").containsEntry("sslcert", "")
                .containsEntry("sslmode", "prefer").containsEntry("connectTimeout", "2").containsEntry("socketTimeout", "2")
                .containsEntry("loginTimeout", "2").containsEntry("readOnly", "true").containsEntry("ApplicationName", "grantforge")
                .doesNotContainKeys("sslkey", "sslrootcert", "sslfactory");
        assertThat(PostgreSqlClient.properties(settings(PostgreSqlSettings.TIMEOUT, "1"))).as("no password, a timeout of at least a second")
                .doesNotContainKey("password").containsEntry("connectTimeout", "1");
    }

    @Test
    void checksTheServerAgainstTheCaFileOrTheJvm()
    {
        assertThat(PostgreSqlClient.properties(settings(PostgreSqlSettings.SSL_MODE, "verify-full", PostgreSqlSettings.ROOT_CERTIFICATE, "/ca.pem")))
                .containsEntry("sslrootcert", "/ca.pem").doesNotContainKey("sslfactory");
        assertThat(PostgreSqlClient.properties(settings(PostgreSqlSettings.SSL_MODE, "verify-ca"))).containsEntry("sslfactory", PostgreSqlClient.JAVA_TRUST)
                .doesNotContainKey("sslrootcert");
    }

    @Test
    void signsInWithACertificate()
    {
        Properties properties = PostgreSqlClient.properties(settings(PostgreSqlSettings.AUTHENTICATION, "certificate", PostgreSqlSettings.SSL_MODE,
                "require", PostgreSqlSettings.CLIENT_CERTIFICATE, "/c.pem", PostgreSqlSettings.CLIENT_KEY, "/c.key"));
        assertThat(properties).containsEntry("sslcert", "/c.pem").containsEntry("sslkey", "/c.key").doesNotContainKey("password");
    }

    @ParameterizedTest
    @CsvSource({"28P01, AUTHENTICATION_FAILED", "28000, AUTHENTICATION_FAILED", "42501, ACCESS_DENIED", "3D000, NOT_FOUND",
            "08001, UNREACHABLE", "57P03, UNREACHABLE", "53300, UNREACHABLE", "42P01, FAILED"})
    void namesWhyARequestFailed(String state, LookupException.Reason reason)
    {
        LookupException failure = PostgreSqlClient.failure("pg1:5432/sales", new SQLException("refused\nDetail: more", state));
        assertThat(failure.getReason()).isEqualTo(reason);
        assertThat(failure).hasMessage("pg1:5432/sales: refused");
        assertThat(PostgreSqlClient.failure("pg1:5432/sales", new SQLException()).getReason()).isEqualTo(LookupException.Reason.FAILED);
        assertThat(PostgreSqlClient.message(new IllegalStateException())).isEqualTo("IllegalStateException");
    }

    @Test
    void goesToTheNextServerOnlyWhenOneCannotBeReached() throws SQLException
    {
        Connection connection = mock(Connection.class);
        List<String> tried = new ArrayList<>();
        PostgreSqlClient client = new PostgreSqlClient(settings(PostgreSqlSettings.HOSTS, "pg1,pg2,pg3"), (url, properties) -> {
            tried.add(url);
            if (url.contains("pg1")) {
                throw new SQLException("Connection refused", "08001");
            }
            return connection;
        });
        String read = client.run("sales", signedIn -> "read");
        assertThat(read).isEqualTo("read");
        assertThat(tried).containsExactly("jdbc:postgresql://pg1:5432/sales", "jdbc:postgresql://pg2:5432/sales");
        verify(connection).setReadOnly(true);
        verify(connection).close();

        PostgreSqlClient refusing = new PostgreSqlClient(settings(PostgreSqlSettings.HOSTS, "pg1,pg2"), (url, properties) -> {
            throw new SQLException("password authentication failed", "28P01");
        });
        assertThatThrownBy(() -> refusing.run("sales", signedIn -> "read")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED)).hasMessageStartingWith("pg1:5432/sales");
    }

    @Test
    void failsWhenNoServerCanBeReachedOrTheWorkFails()
    {
        PostgreSqlClient down = new PostgreSqlClient(settings(PostgreSqlSettings.HOSTS, "pg1,pg2"), (url, properties) -> {
            throw new SQLException("Connection refused " + url, "08001");
        });
        assertThatThrownBy(() -> down.run("sales", signedIn -> "read")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE)).hasMessageContaining("pg1:5432");
        PostgreSqlClient failing = new PostgreSqlClient(settings(), (url, properties) -> mock(Connection.class));
        assertThatThrownBy(() -> failing.run("sales", signedIn -> {
            throw new SQLException("relation does not exist", "42P01");
        })).isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.FAILED));
        PostgreSqlClient foreign = new PostgreSqlClient(settings(), (url, properties) -> null);
        assertThatThrownBy(() -> foreign.run("sales", signedIn -> "read")).isInstanceOf(LookupException.class).hasMessageContaining("does not accept");
    }

    @Test
    void theRealDriverReportsAServerThatCannotBeReached()
    {
        PostgreSqlClient client = new PostgreSqlClient(settings(PostgreSqlSettings.HOSTS, "127.0.0.1:1", PostgreSqlSettings.TIMEOUT, "2000"));
        assertThatThrownBy(() -> client.run("postgres", signedIn -> "read")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
    }
}
