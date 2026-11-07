// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosTicket;
import javax.security.auth.login.LoginException;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Signs principals in against a real KDC, with a keytab and with a password, the realm coming from the JVM's Kerberos settings. */
class KerberosLoginTest
{
    private static final String REALM = "EXAMPLE.COM";
    private static final String KEYTAB_PRINCIPAL = "lookup@" + REALM;
    private static final String PASSWORD_PRINCIPAL = "grantforge@" + REALM;
    private static final String SECRET = "kerberos-secret";
    private static final String KRB5_CONF = "java.security.krb5.conf";

    @TempDir
    static Path work;

    private static SimpleKdcServer kdc;
    private static File keytab;
    private static @Nullable String before;

    @BeforeAll
    static void startKdc() throws Exception
    {
        int port;
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
        // The export takes every principal there is, so the password principal comes after it.
        keytab = work.resolve("lookup.keytab").toFile();
        kdc.createAndExportPrincipals(keytab, KEYTAB_PRINCIPAL);
        kdc.createPrincipal(PASSWORD_PRINCIPAL, SECRET);
        before = System.getProperty(KRB5_CONF);
        System.setProperty(KRB5_CONF, Files.writeString(work.resolve("test-krb5.conf"), """
                [libdefaults]
                default_realm = %s
                udp_preference_limit = 1
                [realms]
                %s = {
                  kdc = localhost:%d
                }
                """.formatted(REALM, REALM, port)).toString());
    }

    @AfterAll
    static void stopKdc() throws Exception
    {
        if (before == null) {
            System.clearProperty(KRB5_CONF);
        }
        else {
            System.setProperty(KRB5_CONF, before);
        }
        kdc.stop();
    }

    private static boolean ticketed(Subject subject)
    {
        return !subject.getPrivateCredentials(KerberosTicket.class).isEmpty();
    }

    @Test
    void signsInWithAKeytab() throws Exception
    {
        assertThat(ticketed(KerberosLogin.login(KEYTAB_PRINCIPAL, keytab.getPath(), null))).isTrue();
    }

    @Test
    void signsInWithAPassword() throws Exception
    {
        assertThat(ticketed(KerberosLogin.login(PASSWORD_PRINCIPAL, null, SECRET))).isTrue();
    }

    @Test
    void reportsWhatCannotSignIn()
    {
        assertThatThrownBy(() -> KerberosLogin.login(PASSWORD_PRINCIPAL, null, "wrong")).isInstanceOf(LoginException.class);
        assertThatThrownBy(() -> KerberosLogin.login(PASSWORD_PRINCIPAL, null, null)).isInstanceOf(LoginException.class).hasMessageContaining("password or a keytab");
        assertThatThrownBy(() -> KerberosLogin.login(KEYTAB_PRINCIPAL, work.resolve("missing.keytab").toString(), null)).isInstanceOf(IOException.class);
    }
}
