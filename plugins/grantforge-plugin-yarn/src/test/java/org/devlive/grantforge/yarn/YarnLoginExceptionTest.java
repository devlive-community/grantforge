// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn;

import org.devlive.grantforge.plugin.api.KerberosRealms;
import org.devlive.grantforge.plugin.api.LookupException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class YarnLoginExceptionTest
{
    @TempDir
    Path temporary;

    @AfterEach
    void forget()
    {
        YarnClusterClient.forgetLogins();
        KerberosRealms.forget();
    }

    @Test
    void keepsTheSignInFailureWhenItIsWrapped()
    {
        IOException credentials = new IOException("KDC returned no usable credentials");
        YarnLoginException login = new YarnLoginException("Kerberos refused grantforge@EXAMPLE.COM", credentials);
        IOException transport = new IOException("YARN could not open the session", login);
        LookupException failed = YarnClusterClient.failure(transport);
        assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(failed).hasMessage("YARN could not open the session").hasCause(transport);
        assertThat(login).hasMessage("Kerberos refused grantforge@EXAMPLE.COM").hasCause(credentials);
    }

    @Test
    void returnsAMissingKeytabBeforeStartingAClient()
    {
        YarnClusterClient.Opener opener = mock(YarnClusterClient.Opener.class);
        Path keytab = temporary.resolve("missing.keytab");
        YarnSettings settings = YarnSettings.of(YarnSettingsTest.config(YarnSettings.AUTHENTICATION, "kerberos", YarnSettings.USER,
                "grantforge@EXAMPLE.COM", YarnSettings.KEYTAB, keytab.toString(), YarnSettings.KDC, "localhost:1"));
        assertThatThrownBy(() -> new YarnClusterClient(settings, opener).run(client -> "never")).isInstanceOfSatisfying(LookupException.class,
                failed -> {
                    assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
                    assertThat(failed).hasMessageContaining(keytab.toString()).hasMessageContaining("cannot be read")
                            .hasCauseInstanceOf(YarnLoginException.class);
                });
        verifyNoInteractions(opener);
    }
}
