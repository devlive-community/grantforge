// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.devlive.grantforge.identity.application.SecretBox;
import org.devlive.grantforge.identity.domain.PlatformSetting;
import org.devlive.grantforge.identity.domain.PlatformSettingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SnapshotSignerTest
{
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Autowired
    private PlatformSettingRepository settings;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void deleteSettings()
    {
        settings.deleteAll();
    }

    private SnapshotSigner signer()
    {
        return new SnapshotSigner(settings, new SecretBox(KEY, settings, transactionManager), transactionManager);
    }

    private static boolean verifies(SigningKey key, byte[] data, String signature) throws Exception
    {
        PublicKey publicKey = KeyFactory.getInstance(key.algorithm()).generatePublic(new X509EncodedKeySpec(Base64.getDecoder()
                .decode(key.publicKey())));
        Signature verifier = Signature.getInstance(key.algorithm());
        verifier.initVerify(publicKey);
        verifier.update(data);
        return verifier.verify(Base64.getDecoder().decode(signature));
    }

    @Test
    void signsWithOneKeyPairEveryNodeShares() throws Exception
    {
        byte[] data = "{\"policies\":[]}".getBytes(StandardCharsets.UTF_8);
        SnapshotSigner first = signer();
        String signature = first.sign(data);
        SigningKey key = first.publicKey();
        assertThat(key.algorithm()).isEqualTo("Ed25519");
        assertThat(key.keyId()).hasSize(16);
        assertThat(verifies(key, data, signature)).isTrue();
        assertThat(verifies(key, "{}".getBytes(StandardCharsets.UTF_8), signature)).isFalse();

        // Another node reads the same pair; the private key is stored sealed.
        SnapshotSigner second = signer();
        assertThat(second.publicKey()).isEqualTo(key);
        assertThat(verifies(key, data, second.sign(data))).isTrue();
        String stored = settings.findBySettingKey(SnapshotSigner.SETTING).orElseThrow().getSettingValue();
        assertThat(stored).startsWith("ed25519:" + key.publicKey() + ":v1:");
    }

    @Test
    void refusesUnreadableKeys()
    {
        settings.saveAndFlush(PlatformSetting.of(SnapshotSigner.SETTING, "rsa:whatever"));
        assertThatThrownBy(() -> signer().publicKey()).isInstanceOf(IllegalStateException.class);
        settings.deleteAll();
        settings.saveAndFlush(PlatformSetting.of(SnapshotSigner.SETTING, "ed25519:AAAA:v1:broken"));
        assertThatThrownBy(() -> signer().sign(new byte[1])).isInstanceOf(IllegalStateException.class);
    }
}
