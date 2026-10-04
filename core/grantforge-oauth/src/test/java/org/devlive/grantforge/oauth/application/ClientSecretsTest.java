// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

class ClientSecretsTest
{
    private final PasswordEncoder hashes = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void acceptsTheCurrentAndThePreviousSecret()
    {
        String current = requireNonNull(hashes.encode("new"));
        String previous = requireNonNull(hashes.encode("old"));
        ClientSecrets secrets = new ClientSecrets(hashes);

        assertThat(secrets.matches("new", ClientSecrets.join(current, previous))).isTrue();
        assertThat(secrets.matches("old", ClientSecrets.join(current, previous))).isTrue();
        assertThat(secrets.matches("old", ClientSecrets.join(current, null))).isFalse();
        assertThat(secrets.matches("other", ClientSecrets.join(current, previous))).isFalse();
        assertThat(secrets.matches(null, current)).isFalse();
        assertThat(secrets.matches("new", null)).isFalse();
        assertThat(secrets.matches("x", secrets.encode("x"))).isTrue();
        assertThat(secrets.upgradeEncoding(current)).isFalse();
    }
}
