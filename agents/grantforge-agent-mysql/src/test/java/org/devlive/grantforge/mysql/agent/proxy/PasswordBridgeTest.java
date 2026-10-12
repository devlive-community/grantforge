// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.proxy;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordBridgeTest
{
    private static final byte[] SCRAMBLE = "mysql-authentication".getBytes(StandardCharsets.US_ASCII);

    @Test
    void carriesTheExactPasswordAcrossDifferentTlsModes() throws GeneralSecurityException
    {
        PasswordBridge server = new PasswordBridge();
        byte[] password = "  密码 with spaces  \0".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = PasswordBridge.encrypt(password, SCRAMBLE, server.publicKey());
        assertThat(encrypted).hasSize(256).isNotEqualTo(password);
        assertThat(server.decrypt(encrypted, SCRAMBLE)).containsExactly(password);
        assertThat(password).containsExactly("  密码 with spaces  \0".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void supportsAnEmptyPasswordAndRepeatsTheScramble() throws GeneralSecurityException
    {
        PasswordBridge server = new PasswordBridge();
        byte[] empty = {0};
        assertThat(server.decrypt(PasswordBridge.encrypt(empty, SCRAMBLE, server.publicKey()), SCRAMBLE)).containsExactly(empty);
        byte[] longPassword = ("password".repeat(8) + "\0").getBytes(StandardCharsets.US_ASCII);
        assertThat(server.decrypt(PasswordBridge.encrypt(longPassword, SCRAMBLE, server.publicKey()), SCRAMBLE)).containsExactly(longPassword);
    }

    @Test
    void doesNotExposeItsMutablePublicKeyBytes() throws GeneralSecurityException
    {
        PasswordBridge server = new PasswordBridge();
        byte[] original = server.publicKey();
        byte[] changed = server.publicKey();
        changed[0] = 0;
        assertThat(server.publicKey()).containsExactly(original);
        byte[] password = "secret\0".getBytes(StandardCharsets.US_ASCII);
        assertThat(server.decrypt(PasswordBridge.encrypt(password, SCRAMBLE, server.publicKey()), SCRAMBLE)).containsExactly(password);
    }

    @Test
    void rejectsCiphertextFromAnotherServerAndInvalidPublicKeys() throws GeneralSecurityException
    {
        PasswordBridge server = new PasswordBridge();
        PasswordBridge other = new PasswordBridge();
        byte[] encrypted = PasswordBridge.encrypt(new byte[] {'a', 0}, SCRAMBLE, other.publicKey());
        assertThatThrownBy(() -> server.decrypt(encrypted, SCRAMBLE)).isInstanceOf(GeneralSecurityException.class);
        assertThatThrownBy(() -> PasswordBridge.encrypt(new byte[] {0}, SCRAMBLE, "not a key".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOfAny(GeneralSecurityException.class, IllegalArgumentException.class);
    }
}
