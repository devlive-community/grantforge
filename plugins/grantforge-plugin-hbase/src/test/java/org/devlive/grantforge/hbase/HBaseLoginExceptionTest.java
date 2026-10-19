// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hbase;

import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.security.UserGroupInformation;
import org.devlive.grantforge.plugin.api.KerberosRealms;
import org.devlive.grantforge.plugin.api.LookupException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.security.PrivilegedExceptionAction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HBaseLoginExceptionTest
{
    @TempDir
    Path temporary;

    @AfterEach
    void forget()
    {
        HBaseClient.forgetLogins();
        KerberosRealms.forget();
    }

    @Test
    void keepsTheSignInFailureWhenItIsWrappedByHBase()
    {
        IOException credentials = new IOException("KDC returned no usable credentials");
        HBaseLoginException login = new HBaseLoginException("Kerberos refused grantforge@EXAMPLE.COM", credentials);
        IOException transport = new IOException("HBase could not open the session", login);

        LookupException failed = HBaseClient.failure(transport);
        assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(failed).hasMessage("HBase could not open the session").hasCause(transport);
        assertThat(transport).hasCause(login);
        assertThat(login).hasMessage("Kerberos refused grantforge@EXAMPLE.COM").hasCause(credentials);
    }

    @Test
    void returnsAMissingKeytabAsAnAuthenticationFailureBeforeOpeningAConnection()
    {
        HBaseClient.Opener opener = mock(HBaseClient.Opener.class);
        Path keytab = temporary.resolve("missing.keytab");
        HBaseSettings settings = HBaseSettings.of(HBaseSettingsTest.config(HBaseSettings.AUTHENTICATION, "kerberos", HBaseSettings.USER,
                "grantforge@EXAMPLE.COM", HBaseSettings.KEYTAB, keytab.toString(), HBaseSettings.KDC, "localhost:1"));

        assertThatThrownBy(() -> new HBaseClient(settings, opener).run(admin -> "never"))
                .isInstanceOfSatisfying(LookupException.class, failed -> {
                    assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
                    assertThat(failed).hasMessageContaining(keytab.toString()).hasMessageContaining("cannot be read")
                            .hasCauseInstanceOf(HBaseLoginException.class);
                    assertThat(failed.getCause()).hasNoCause();
                });
        verifyNoInteractions(opener);
    }

    @Test
    void restoresTheCallingUserAndClosesResourcesAfterAnAuthenticationFailure() throws Exception
    {
        Connection connection = mock(Connection.class);
        Admin admin = mock(Admin.class);
        when(connection.getAdmin()).thenReturn(admin);
        HBaseClient client = new HBaseClient(HBaseSettings.of(HBaseSettingsTest.config()), (configuration, user) -> connection);
        HBaseLoginException login = new HBaseLoginException("Kerberos credentials expired", new IOException("ticket renewal failed"));
        UserGroupInformation callingUser = UserGroupInformation.createRemoteUser("calling-user");
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        ClassLoader callingLoader = mock(ClassLoader.class);
        Thread.currentThread().setContextClassLoader(callingLoader);
        try {
            callingUser.doAs((PrivilegedExceptionAction<Void>) () -> {
                assertThatThrownBy(() -> client.run(opened -> {
                    assertThat(UserGroupInformation.getCurrentUser().getShortUserName()).isEqualTo("grantforge");
                    assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(HBaseClient.class.getClassLoader());
                    throw login;
                })).isInstanceOfSatisfying(LookupException.class, failed -> {
                    assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
                    assertThat(failed).hasMessage("Kerberos credentials expired").hasCause(login);
                });
                assertThat(UserGroupInformation.getCurrentUser().getShortUserName()).isEqualTo("calling-user");
                assertThat(Thread.currentThread().getContextClassLoader()).isSameAs(callingLoader);
                return null;
            });
        }
        finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
        verify(admin).close();
        verify(connection).close();
    }
}
