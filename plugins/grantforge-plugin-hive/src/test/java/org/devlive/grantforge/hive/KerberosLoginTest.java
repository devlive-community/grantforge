// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive;

import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;
import org.devlive.grantforge.plugin.api.KerberosRealms;
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

/** Signs principals in against a real KDC, with a keytab, with a password and through KDCs a service names. */
class KerberosLoginTest
{
    private static final String REALM = "EXAMPLE.COM";
    private static final String KEYTAB_PRINCIPAL = "lookup@" + REALM;
    private static final String PASSWORD_PRINCIPAL = "grantforge@" + REALM;
    private static final String SECRET = "kerberos-secret";

    @TempDir
    static Path work;

    private static SimpleKdcServer kdc;
    private static File keytab;
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
        // The export takes every principal there is, so the password principal comes after it.
        keytab = work.resolve("lookup.keytab").toFile();
        kdc.createAndExportPrincipals(keytab, KEYTAB_PRINCIPAL);
        kdc.createPrincipal(PASSWORD_PRINCIPAL, SECRET);
    }

    @AfterAll
    static void stopKdc() throws Exception
    {
        KerberosRealms.forget();
        kdc.stop();
    }

    private static boolean ticketed(Subject subject)
    {
        return !subject.getPrivateCredentials(KerberosTicket.class).isEmpty();
    }

    @Test
    void signsInWithAKeytabThroughTheServicesKdc() throws Exception
    {
        Subject subject = KerberosLogin.login(KEYTAB_PRINCIPAL, keytab.getPath(), null, "localhost:" + port);
        assertThat(ticketed(subject)).isTrue();
        assertThat(Files.readString(Path.of(System.getProperty(KerberosRealms.CONFIGURATION)))).contains(REALM, "localhost:" + port);
    }

    @Test
    void signsInWithAPassword() throws Exception
    {
        assertThat(ticketed(KerberosLogin.login(PASSWORD_PRINCIPAL, null, SECRET, "localhost:" + port))).isTrue();
    }

    @Test
    void refusesWrongPasswordsAndMissingCredentials()
    {
        assertThatThrownBy(() -> KerberosLogin.login(PASSWORD_PRINCIPAL, null, "wrong", "localhost:" + port)).isInstanceOf(LoginException.class);
        assertThatThrownBy(() -> KerberosLogin.login(PASSWORD_PRINCIPAL, null, null, "localhost:" + port)).isInstanceOf(LoginException.class)
                .hasMessageContaining("password or a keytab");
        assertThatThrownBy(() -> KerberosLogin.login(KEYTAB_PRINCIPAL, work.resolve("missing.keytab").toString(), null, null))
                .isInstanceOf(IOException.class).hasMessageContaining("cannot be read");
    }
}
