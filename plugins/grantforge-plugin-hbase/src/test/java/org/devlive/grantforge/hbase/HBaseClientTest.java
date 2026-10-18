// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase;

import org.apache.hadoop.hbase.MasterNotRunningException;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.exceptions.ConnectionClosedException;
import org.apache.hadoop.hbase.security.AccessDeniedException;
import org.apache.hadoop.hbase.security.User;
import org.apache.hadoop.security.UserGroupInformation;
import org.devlive.grantforge.plugin.api.KerberosRealms;
import org.devlive.grantforge.plugin.api.LookupException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;
import javax.security.auth.login.LoginException;
import javax.security.sasl.SaslException;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HBaseClientTest
{
    private final Connection connection = mock(Connection.class);
    private final Admin admin = mock(Admin.class);

    @AfterEach
    void forget()
    {
        HBaseClient.forgetLogins();
        KerberosRealms.forget();
    }

    private HBaseClient client(String... values) throws IOException
    {
        when(connection.getAdmin()).thenReturn(admin);
        return new HBaseClient(HBaseSettings.of(HBaseSettingsTest.config(values)), (configuration, user) -> connection);
    }

    private static LookupException.Reason reason(HBaseClient client, Exception thrown)
    {
        try {
            client.run(admin -> {
                if (thrown instanceof IOException io) {
                    throw io;
                }
                throw (RuntimeException) thrown;
            });
        }
        catch (LookupException failed) {
            return failed.getReason();
        }
        throw new AssertionError("no failure");
    }

    @Test
    void runsAsTheUserWithThePluginsClassLoaderAndClosesTheConnection() throws Exception
    {
        AtomicReference<User> as = new AtomicReference<>();
        AtomicReference<ClassLoader> loader = new AtomicReference<>();
        when(connection.getAdmin()).thenReturn(admin);
        HBaseClient client = new HBaseClient(HBaseSettings.of(HBaseSettingsTest.config()), (configuration, user) -> {
            as.set(user);
            assertThat(configuration.get(HBaseSettings.QUORUM)).isEqualTo("zk1,zk2:2182");
            return connection;
        });
        ClassLoader before = Thread.currentThread().getContextClassLoader();
        String result = client.run(admin -> {
            loader.set(Thread.currentThread().getContextClassLoader());
            assertThat(UserGroupInformation.getCurrentUser().getShortUserName()).isEqualTo("grantforge");
            return "done";
        });
        assertThat(result).isEqualTo("done");
        assertThat(requireNonNull(as.get(), "user").getShortName()).isEqualTo("grantforge");
        assertThat(loader.get()).isSameAs(HBaseClient.class.getClassLoader());
        assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(before);
        verify(admin).close();
        verify(connection).close();
    }

    @Test
    void namesFailuresByKind() throws Exception
    {
        HBaseClient client = client();
        assertThat(reason(client, new AccessDeniedException("no"))).isEqualTo(LookupException.Reason.ACCESS_DENIED);
        assertThat(reason(client, new IOException("wrapped", new ConnectException("refused")))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(client, new MasterNotRunningException("down"))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(client, new IOException(new SaslException("GSS")))).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(reason(client, new IllegalStateException("odd"))).isEqualTo(LookupException.Reason.FAILED);
        assertThat(reason(client, new IOException("plain"))).isEqualTo(LookupException.Reason.FAILED);
    }

    @Test
    void takesAClosedConnectionForARefusedSignIn() throws Exception
    {
        assertThatThrownBy(() -> client().run(admin -> {
            throw new MasterNotRunningException(new ConnectionClosedException("Connection closed"));
        })).isInstanceOfSatisfying(LookupException.class, failed -> {
            assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
            assertThat(failed).hasMessageContaining("hbase.rpc.protection");
        });
    }

    @Test
    void passesOnALookupFailureAndReadsTheFirstLine()
    {
        LookupException lookup = new LookupException(LookupException.Reason.INVALID_INPUT, "bad");
        assertThat(HBaseClient.failure(lookup)).isSameAs(lookup);
        assertThat(HBaseClient.failure(new LoginException("refused")).getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(HBaseClient.message(new IOException("first\nsecond"))).isEqualTo("first");
        assertThat(HBaseClient.message(new IOException())).isEqualTo("IOException");
    }

    @Test
    void stopsWaitingWhenInterrupted() throws Exception
    {
        HBaseClient client = client();
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> client.run(admin -> "never")).isInstanceOfSatisfying(LookupException.class,
                    failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.FAILED));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        }
        finally {
            Thread.interrupted();
        }
    }

    @Test
    void refusesAKerberosSignInWithoutUsableCredentials() throws Exception
    {
        HBaseClient keytab = client(HBaseSettings.AUTHENTICATION, "kerberos", HBaseSettings.USER, "grantforge@EXAMPLE.COM", HBaseSettings.KEYTAB,
                "/nowhere/grantforge.keytab", HBaseSettings.KDC, "localhost:1");
        assertThatThrownBy(() -> keytab.run(admin -> "never")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
        HBaseClient password = client(HBaseSettings.AUTHENTICATION, "kerberos", HBaseSettings.USER, "grantforge@EXAMPLE.COM",
                HBaseSettings.PASSWORD, "secret", HBaseSettings.KDC, "localhost:1");
        assertThatThrownBy(() -> password.run(admin -> "never")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
    }

    @Test
    void usesAPasswordsTicketWhileAFifthOfItsLifeIsLeft()
    {
        Instant now = Instant.parse("2026-10-18T12:00:00Z");
        KerberosTicket fresh = ticket("krbtgt/EXAMPLE.COM@EXAMPLE.COM", now.minusSeconds(3600), now.plusSeconds(3 * 3600));
        assertThat(HBaseClient.CachedLogin.lasts(fresh, now)).isTrue();
        assertThat(HBaseClient.CachedLogin.lasts(ticket("krbtgt/EXAMPLE.COM@EXAMPLE.COM", now.minusSeconds(4 * 3600), now.plusSeconds(1800)), now))
                .isFalse();
        assertThat(HBaseClient.CachedLogin.lasts(ticket("krbtgt/EXAMPLE.COM@EXAMPLE.COM", now.minusSeconds(100), now.plusSeconds(50)), now))
                .as("at least a minute").isFalse();
        assertThat(HBaseClient.CachedLogin.lasts(ticket("hbase/host@EXAMPLE.COM", now.minusSeconds(60), now.plusSeconds(7200)), now)).isFalse();
        KerberosTicket undated = ticket("krbtgt/EXAMPLE.COM@EXAMPLE.COM", now, now);
        when(undated.getEndTime()).thenReturn(null);
        assertThat(HBaseClient.CachedLogin.lasts(undated, now)).isFalse();
        Subject subject = new Subject();
        subject.getPrivateCredentials().add(fresh);
        assertThat(new HBaseClient.CachedLogin(UserGroupInformation.createRemoteUser("grantforge"), subject).usable(now)).isTrue();
        assertThat(new HBaseClient.CachedLogin(UserGroupInformation.createRemoteUser("grantforge"), new Subject()).usable(now)).isFalse();
    }

    private static KerberosTicket ticket(String server, Instant start, Instant end)
    {
        KerberosTicket ticket = mock(KerberosTicket.class);
        when(ticket.getServer()).thenReturn(new KerberosPrincipal(server));
        when(ticket.getStartTime()).thenReturn(Date.from(start));
        when(ticket.getEndTime()).thenReturn(Date.from(end));
        return ticket;
    }
}
