// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino;

import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;
import org.devlive.grantforge.plugin.api.KerberosRealms;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLHandshakeException;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TrinoClientTest
{
    private static final String REALM = "EXAMPLE.COM";
    private static final String PRINCIPAL = "grantforge@" + REALM;
    private static final String SECRET = "kerberos-secret";

    @TempDir
    static Path work;

    private static SimpleKdcServer kdc;
    private static int port;

    @BeforeAll
    static void startKdc() throws Exception
    {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        kdc = new SimpleKdcServer();
        kdc.setWorkDir(work.toFile());
        kdc.setKdcRealm(REALM);
        kdc.setKdcHost("localhost");
        kdc.setAllowUdp(false);
        kdc.setKdcTcpPort(port);
        kdc.init();
        kdc.start();
        kdc.createPrincipal(PRINCIPAL, SECRET);
    }

    @AfterAll
    static void stopKdc() throws Exception
    {
        KerberosRealms.forget();
        kdc.stop();
    }

    private static TrinoSettings settings(String... values)
    {
        Map<String, String> map = new HashMap<>(Map.of(TrinoSettings.URL, "jdbc:trino://trino.example.com:8443", TrinoSettings.USER, "lookup"));
        for (int index = 0; index < values.length; index += 2) {
            map.put(values[index], values[index + 1]);
        }
        return TrinoSettings.of(new ServiceConfig("warehouse", map));
    }

    private static TrinoClient.Login refusing()
    {
        return (principal, keytab, password, kdcs) -> {
            throw new AssertionError("no Kerberos sign-in expected");
        };
    }

    private static LookupException.Reason reason(Throwable failure)
    {
        return TrinoClient.failure(failure).getReason();
    }

    @Test
    void connectsRunsTheWorkAndClosesTheConnection() throws Exception
    {
        Connection connection = mock(Connection.class);
        AtomicReference<String> url = new AtomicReference<>();
        AtomicReference<Properties> properties = new AtomicReference<>();
        TrinoClient client = new TrinoClient(settings(), (given, props) -> {
            url.set(given);
            properties.set(props);
            return connection;
        }, refusing());
        Connection used = client.run(open -> open);
        assertThat(used).isSameAs(connection);
        assertThat(url.get()).isEqualTo("jdbc:trino://trino.example.com:8443");
        assertThat(properties.get()).containsEntry("user", "lookup").doesNotContainKeys(TrinoClient.DELEGATION, TrinoClient.DELEGATING);
        verify(connection).close();
    }

    @Test
    void handsTheDriverTheCredentialOfTheKerberosSignIn() throws Exception
    {
        AtomicReference<Properties> properties = new AtomicReference<>();
        TrinoClient client = new TrinoClient(settings(TrinoSettings.AUTHENTICATION, "kerberos", TrinoSettings.SSL, "true", TrinoSettings.USER, PRINCIPAL,
                TrinoSettings.PASSWORD, SECRET, TrinoSettings.KDC, "localhost:" + port), (given, props) -> {
            properties.set(props);
            return mock(Connection.class);
        }, KerberosLogin::login);
        client.run(connection -> "done");
        Properties driver = requireNonNull(properties.get(), "properties");
        assertThat(driver).containsEntry("user", "grantforge").containsEntry(TrinoClient.DELEGATING, "true").doesNotContainKey("password");
        GSSCredential credential = (GSSCredential) requireNonNull(driver.get(TrinoClient.DELEGATION), "credential");
        assertThat(credential.getUsage()).isEqualTo(GSSCredential.INITIATE_ONLY);
        assertThat(credential.getName()).hasToString(PRINCIPAL);
    }

    @Test
    void namesASignInTheKdcRefuses()
    {
        TrinoClient refused = new TrinoClient(settings(TrinoSettings.AUTHENTICATION, "kerberos", TrinoSettings.SSL, "true", TrinoSettings.USER, PRINCIPAL,
                TrinoSettings.PASSWORD, "wrong", TrinoSettings.KDC, "localhost:" + port), (given, props) -> mock(Connection.class),
                KerberosLogin::login);
        assertThatThrownBy(() -> refused.run(connection -> "done")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
        TrinoClient ticketless = new TrinoClient(settings(TrinoSettings.AUTHENTICATION, "kerberos", TrinoSettings.SSL, "true", TrinoSettings.USER,
                PRINCIPAL, TrinoSettings.PASSWORD, SECRET), (given, props) -> mock(Connection.class), (principal, keytab, password, kdcs) -> new Subject());
        assertThatThrownBy(() -> ticketless.run(connection -> "done")).as("a subject without a ticket has no credential")
                .isInstanceOfSatisfying(LookupException.class,
                        failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
    }

    @Test
    void refusesAUrlTheDriverDoesNotTake()
    {
        TrinoClient client = new TrinoClient(settings(), (given, props) -> null, refusing());
        assertThatThrownBy(() -> client.run(connection -> "done")).isInstanceOf(LookupException.class)
                .hasMessage("Trino's driver does not take jdbc:trino://trino.example.com:8443");
    }

    @Test
    void passesALookupFailureOn()
    {
        LookupException invalid = new LookupException(LookupException.Reason.INVALID_INPUT, "bad", null);
        TrinoClient client = new TrinoClient(settings(), (given, props) -> mock(Connection.class), refusing());
        assertThatThrownBy(() -> client.run(connection -> {
            throw invalid;
        })).isSameAs(invalid);
    }

    @Test
    void namesFailuresByTheirKind()
    {
        assertThat(reason(new SQLException("Authentication failed: Access Denied: Invalid credentials"))).isEqualTo(
                LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(reason(new LoginException("Pre-authentication information was invalid"))).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(reason(new SQLException("wrapped", new GSSException(GSSException.NO_CRED)))).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(reason(new SQLException("Access Denied: Cannot access catalog hive"))).isEqualTo(LookupException.Reason.ACCESS_DENIED);
        assertThat(reason(new SQLException("Error executing query", new ConnectException("Connection refused")))).isEqualTo(
                LookupException.Reason.UNREACHABLE);
        assertThat(reason(new SQLException("x", new UnknownHostException("trino")))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new SQLException("x", new NoRouteToHostException("trino")))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new SQLException("x", new SocketTimeoutException("timeout")))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new SQLException("x", new SSLHandshakeException("PKIX path building failed")))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new SQLException("Query failed: line 1:1: mismatched input"))).isEqualTo(LookupException.Reason.FAILED);
        assertThat(reason(new IOException())).isEqualTo(LookupException.Reason.FAILED);
    }

    @Test
    void keepsTheFirstLineOfAMessage()
    {
        assertThat(TrinoClient.message(new SQLException("Query failed\n\tat trino.Server"))).isEqualTo("Query failed");
        assertThat(TrinoClient.message(new SQLException("one line"))).isEqualTo("one line");
        assertThat(TrinoClient.message(new IOException())).isEqualTo("IOException");
        assertThat(TrinoClient.failure(new SQLException("Access Denied: x\nmore"))).hasMessage("Access Denied: x").hasCauseInstanceOf(SQLException.class);
    }

    @Test
    void usesTrinosDriverByDefault()
    {
        TrinoClient client = new TrinoClient(settings(TrinoSettings.URL, "jdbc:trino://localhost:" + 1, TrinoSettings.TIMEOUT, "2000"));
        // The driver connects lazily: only a request reaches the coordinator.
        assertThatThrownBy(() -> client.run(connection -> connection.getMetaData().getCatalogs().next())).isInstanceOfSatisfying(
                LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
    }
}
