// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SigningKeyTest
{
    private final KeyPair keys = FakeServer.keyPair();
    private final byte[] data = "{\"format\":1}".getBytes(StandardCharsets.UTF_8);

    @Test
    void checksSignaturesTheJdkMade()
    {
        SigningKey key = SigningKey.of(FakeServer.publicKey(keys));
        String signature = FakeServer.sign(keys, data);

        assertThat(key.verifies(data, signature)).isTrue();
        assertThat(key.verifies("{\"format\":2}".getBytes(StandardCharsets.UTF_8), signature)).isFalse();
        assertThat(key.verifies(data, FakeServer.sign(FakeServer.keyPair(), data))).isFalse();
        assertThat(key.verifies(data, "not base64!")).isFalse();
        assertThat(key.verifies(data, Base64.getEncoder().encodeToString(new byte[10]))).isFalse();
    }

    @Test
    void namesTheKeyAsTheServerDoes()
    {
        String encoded = FakeServer.publicKey(keys);
        SigningKey key = SigningKey.of(" " + encoded + "\n");

        assertThat(key.keyId()).isEqualTo(FakeServer.keyId(keys)).hasSize(16);
        assertThat(key.encoded()).isEqualTo(encoded);
    }

    @Test
    void refusesWhatIsNotAnEd25519Key() throws Exception
    {
        KeyPair rsa = KeyPairGenerator.getInstance("RSA").generateKeyPair();

        assertThatIllegalArgumentException().isThrownBy(() -> SigningKey.of("%%%")).withMessageContaining("X.509");
        assertThatIllegalArgumentException().isThrownBy(() -> SigningKey.of(Base64.getEncoder().encodeToString(new byte[12])))
                .withMessageContaining("X.509");
        assertThatIllegalArgumentException().isThrownBy(() -> SigningKey.of(Base64.getEncoder().encodeToString(rsa.getPublic().getEncoded())))
                .withMessageContaining("not an Ed25519");
    }
}
