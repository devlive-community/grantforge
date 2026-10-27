// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.Collections;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TlsTest
{
    @TempDir
    static Path files;

    private static TestCertificates certificates;

    @BeforeAll
    static void certificates() throws Exception
    {
        certificates = new TestCertificates(files);
        Files.writeString(files.resolve("both.pem"), Files.readString(Path.of(certificates.file("ca.pem")))
                + Files.readString(Path.of(certificates.file("client.pem"))), StandardCharsets.US_ASCII);
        Files.writeString(files.resolve("parameters-then-key.pem"), "-----BEGIN EC PARAMETERS-----\nBggqhkjOPQMBBw==\n-----END EC PARAMETERS-----\n"
                + Files.readString(Path.of(certificates.file("client-pkcs8.pem"))), StandardCharsets.US_ASCII);
    }

    private static Path file(String name)
    {
        return Path.of(requireNonNull(certificates, "certificates").file(name));
    }

    @Test
    void readsEveryCertificateOfAPemFileIntoATrustStore() throws Exception
    {
        KeyStore store = Tls.trustStore(files.resolve("both.pem"));
        assertThat(Collections.list(store.aliases())).containsExactlyInAnyOrder("ca-0", "ca-1");
        assertThat(store.getCertificate("ca-0")).isEqualTo(certificates.ca());
        assertThatThrownBy(() -> Tls.trustStore(file("empty.pem"))).isInstanceOf(GeneralSecurityException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"client-pkcs8.pem", "client-pkcs1.pem", "client-encrypted.pem", "client-legacy-encrypted.pem"})
    void readsAPrivateKeyInEachPemForm(String name) throws Exception
    {
        PrivateKey key = Tls.privateKey(file(name), TestCertificates.PASSPHRASE);
        assertThat(key.getAlgorithm()).isEqualTo("RSA");
        assertThat(Tls.privateKey(file("client-pkcs8.pem"), null).getEncoded()).isEqualTo(key.getEncoded());
    }

    @Test
    void skipsParametersBeforeTheKey() throws Exception
    {
        assertThat(Tls.privateKey(files.resolve("parameters-then-key.pem"), null).getAlgorithm()).isEqualTo("RSA");
    }

    @Test
    void anEncryptedKeyWantsItsPassphrase()
    {
        for (String name : new String[] {"client-encrypted.pem", "client-legacy-encrypted.pem"}) {
            assertThatThrownBy(() -> Tls.privateKey(file(name), null)).isInstanceOf(GeneralSecurityException.class)
                    .hasMessageContaining("give its passphrase");
            assertThatThrownBy(() -> Tls.privateKey(file(name), "")).isInstanceOf(GeneralSecurityException.class);
            assertThatThrownBy(() -> Tls.privateKey(file(name), "wrong")).isInstanceOf(GeneralSecurityException.class)
                    .hasMessageContaining("holds no private key that can be read with the passphrase given");
        }
    }

    @Test
    void aFileWithoutAKeyIsRefused()
    {
        assertThatThrownBy(() -> Tls.privateKey(file("client.pem"), null)).isInstanceOf(GeneralSecurityException.class)
                .hasMessageContaining("holds no PEM private key");
        assertThatThrownBy(() -> Tls.privateKey(file("empty.pem"), null)).isInstanceOf(GeneralSecurityException.class);
    }

    @Test
    void checksNamesInFullModeOnly() throws Exception
    {
        assertThat(Tls.of("full", null, null, null, null).verifier()).isInstanceOf(DefaultHostnameVerifier.class);
        assertThat(Tls.of("certificate", file("ca.pem"), null, null, null).verifier()).isSameAs(NoopHostnameVerifier.INSTANCE);
        assertThat(Tls.of("none", null, file("client.pem"), file("client-pkcs8.pem"), null).verifier()).isSameAs(NoopHostnameVerifier.INSTANCE);
        assertThat(Tls.of("full", file("ca.pem"), file("client.pem"), file("client-encrypted.pem"), TestCertificates.PASSPHRASE).context()
                .getProtocol()).isEqualTo("TLS");
    }
}
