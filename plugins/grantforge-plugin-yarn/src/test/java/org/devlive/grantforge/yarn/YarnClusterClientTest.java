// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn;

import org.apache.hadoop.net.ConnectTimeoutException;
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.exceptions.YarnException;
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
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YarnClusterClientTest
{
    private final YarnClient yarn = mock(YarnClient.class);

    @AfterEach
    void forget()
    {
        YarnClusterClient.forgetLogins();
        KerberosRealms.forget();
    }

    private YarnClusterClient client(String... values)
    {
        return new YarnClusterClient(YarnSettings.of(YarnSettingsTest.config(values)), configuration -> yarn);
    }

    private LookupException.Reason reason(Exception thrown)
    {
        try {
            client().run(started -> {
                if (thrown instanceof IOException io) {
                    throw io;
                }
                if (thrown instanceof YarnException failed) {
                    throw failed;
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
    void runsAsTheUserWithThePluginsClassLoaderAndStopsTheClient() throws Exception
    {
        AtomicReference<ClassLoader> loader = new AtomicReference<>();
        YarnClusterClient client = new YarnClusterClient(YarnSettings.of(YarnSettingsTest.config()), configuration -> {
            assertThat(configuration.get(YarnSettings.RESOURCE_MANAGER)).isEqualTo("rm1.example.com:8032");
            return yarn;
        });
        ClassLoader before = Thread.currentThread().getContextClassLoader();
        String result = client.run(started -> {
            loader.set(Thread.currentThread().getContextClassLoader());
            assertThat(started).isSameAs(yarn);
            assertThat(UserGroupInformation.getCurrentUser().getShortUserName()).isEqualTo("grantforge");
            return "done";
        });
        assertThat(result).isEqualTo("done");
        assertThat(loader.get()).isSameAs(YarnClusterClient.class.getClassLoader());
        assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(before);
        verify(yarn).close();
    }

    @Test
    void namesFailuresByKind()
    {
        assertThat(reason(new AccessControlException("User alice cannot submit"))).isEqualTo(LookupException.Reason.ACCESS_DENIED);
        assertThat(reason(new YarnException("org.apache.hadoop.security.AccessControlException: no"))).isEqualTo(LookupException.Reason.ACCESS_DENIED);
        assertThat(reason(new AccessControlException("Client cannot authenticate via:[TOKEN, KERBEROS]")))
                .isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(reason(new AccessControlException("SIMPLE authentication is not enabled.  Available:[TOKEN, KERBEROS]")))
                .isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(reason(new IOException("GSS initiate failed"))).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(reason(new IOException(new SaslException("GSS")))).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(reason(new IOException("wrapped", new ConnectException("refused")))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new UnknownHostException("rm1"))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new ConnectTimeoutException("slow"))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new YarnException("no such queue"))).isEqualTo(LookupException.Reason.FAILED);
        assertThat(reason(new IllegalStateException("odd"))).isEqualTo(LookupException.Reason.FAILED);
    }

    @Test
    void passesOnALookupFailureAndReadsTheFirstLine()
    {
        LookupException lookup = new LookupException(LookupException.Reason.INVALID_INPUT, "bad");
        assertThat(YarnClusterClient.failure(lookup)).isSameAs(lookup);
        assertThat(YarnClusterClient.failure(new LoginException("refused")).getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(YarnClusterClient.message(new IOException("first\nsecond"))).isEqualTo("first");
        assertThat(YarnClusterClient.message(new IOException())).isEqualTo("IOException");
    }

    @Test
    void stopsWaitingWhenInterrupted()
    {
        YarnClusterClient client = client();
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> client.run(started -> "never")).isInstanceOfSatisfying(LookupException.class,
                    failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.FAILED));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        }
        finally {
            Thread.interrupted();
        }
    }

    @Test
    void refusesAKerberosSignInWithoutUsableCredentials()
    {
        YarnClusterClient password = client(YarnSettings.AUTHENTICATION, "kerberos", YarnSettings.USER, "grantforge@EXAMPLE.COM",
                YarnSettings.PASSWORD, "secret", YarnSettings.KDC, "localhost:1");
        assertThatThrownBy(() -> password.run(started -> "never")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
    }

    @Test
    void usesAPasswordsTicketWhileAFifthOfItsLifeIsLeft()
    {
        Instant now = Instant.parse("2026-10-19T12:00:00Z");
        KerberosTicket fresh = ticket("krbtgt/EXAMPLE.COM@EXAMPLE.COM", now.minusSeconds(3600), now.plusSeconds(3 * 3600));
        assertThat(YarnClusterClient.CachedLogin.lasts(fresh, now)).isTrue();
        assertThat(YarnClusterClient.CachedLogin.lasts(ticket("krbtgt/EXAMPLE.COM@EXAMPLE.COM", now.minusSeconds(4 * 3600), now.plusSeconds(1800)),
                now)).isFalse();
        assertThat(YarnClusterClient.CachedLogin.lasts(ticket("krbtgt/EXAMPLE.COM@EXAMPLE.COM", now.minusSeconds(100), now.plusSeconds(50)), now))
                .as("at least a minute").isFalse();
        assertThat(YarnClusterClient.CachedLogin.lasts(ticket("yarn/host@EXAMPLE.COM", now.minusSeconds(60), now.plusSeconds(7200)), now)).isFalse();
        KerberosTicket undated = ticket("krbtgt/EXAMPLE.COM@EXAMPLE.COM", now, now);
        when(undated.getEndTime()).thenReturn(null);
        assertThat(YarnClusterClient.CachedLogin.lasts(undated, now)).isFalse();
        Subject subject = new Subject();
        subject.getPrivateCredentials().add(fresh);
        assertThat(new YarnClusterClient.CachedLogin(UserGroupInformation.createRemoteUser("grantforge"), subject).usable(now)).isTrue();
        assertThat(new YarnClusterClient.CachedLogin(UserGroupInformation.createRemoteUser("grantforge"), new Subject()).usable(now)).isFalse();
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
