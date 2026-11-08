// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisTlsTest
{
    @TempDir
    static Path files;

    private static TestCertificates certificates;

    @BeforeAll
    static void certificates() throws Exception
    {
        certificates = new TestCertificates(files);
    }

    private static Path file(String name)
    {
        return Path.of(certificates.file(name));
    }

    @Test
    void readsTheCertificatesOfAPemFileIntoATrustStore() throws Exception
    {
        KeyStore store = RedisTls.trustStore(file("ca.pem"));
        assertThat(Collections.list(store.aliases())).containsExactly("ca-0");
        assertThat(store.getCertificate("ca-0")).isEqualTo(certificates.ca());
        assertThatThrownBy(() -> RedisTls.trustStore(file("empty.pem"))).isInstanceOf(GeneralSecurityException.class).hasMessageContaining("no PEM certificate");
    }

    @Test
    void aCertificateWithItsKeyServesAsATrustStoreToo() throws Exception
    {
        assertThat(Collections.list(RedisTls.trustStore(file("client-pkcs8.pem")).aliases())).containsExactly("ca-0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"client-pkcs8.pem", "client-pkcs1.pem", "client-parameters.pem", "client-encrypted.pem", "client-legacy-encrypted.pem"})
    void readsACertificateAndKeyFromOneFileInEachPemForm(String name) throws Exception
    {
        KeyStore store = RedisTls.keyStore(file(name), file(name), TestCertificates.PASSPHRASE);
        assertThat(store.isKeyEntry("client")).isTrue();
        assertThat(store.getKey("client", new char[0]).getAlgorithm()).isEqualTo("RSA");
        assertThat(store.getCertificateChain("client")).hasSize(1);
    }

    @Test
    void readsTheCertificateAndTheKeyFromTwoFiles() throws Exception
    {
        KeyStore store = RedisTls.keyStore(file("client.pem"), file("client-key-only.pem"), null);
        assertThat(store.getCertificateChain("client")).hasSize(1);
    }

    @Test
    void anEncryptedKeyWantsItsPassword()
    {
        for (String name : new String[] {"client-encrypted.pem", "client-legacy-encrypted.pem"}) {
            assertThatThrownBy(() -> RedisTls.keyStore(file(name), file(name), null)).isInstanceOf(GeneralSecurityException.class).hasMessageContaining("give its password");
            assertThatThrownBy(() -> RedisTls.keyStore(file(name), file(name), "")).isInstanceOf(GeneralSecurityException.class);
            assertThatThrownBy(() -> RedisTls.keyStore(file(name), file(name), "wrong")).isInstanceOf(GeneralSecurityException.class)
                    .hasMessageContaining("holds no private key that can be read with the password given");
        }
    }

    @Test
    void aFileWithoutTheKeyOrTheCertificateIsRefused()
    {
        assertThatThrownBy(() -> RedisTls.keyStore(file("client.pem"), file("client.pem"), null)).isInstanceOf(GeneralSecurityException.class).hasMessageContaining("no PEM private key");
        assertThatThrownBy(() -> RedisTls.keyStore(file("client-key-only.pem"), file("client-key-only.pem"), null)).isInstanceOf(GeneralSecurityException.class).hasMessageContaining("no PEM certificate");
    }

    @Test
    void buildsAContextFromWhatIsGiven() throws Exception
    {
        assertThat(RedisTls.context(file("ca.pem"), file("client-pkcs8.pem"), null, null).getProtocol()).isEqualTo("TLS");
        assertThat(RedisTls.context(file("ca.pem"), file("client.pem"), file("client-key-only.pem"), null).getProtocol()).isEqualTo("TLS");
        assertThat(RedisTls.context(file("ca.pem"), null, null, null).getProtocol()).isEqualTo("TLS");
        assertThat(RedisTls.context(null, null, null, null).getProtocol()).isEqualTo("TLS");
    }
}
