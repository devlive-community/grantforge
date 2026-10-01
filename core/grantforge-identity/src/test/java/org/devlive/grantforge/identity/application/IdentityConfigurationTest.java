// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityConfigurationTest
{
    private final IdentityConfiguration configuration = new IdentityConfiguration();

    @Test
    void newPasswordsUseArgon2AndBcryptHashesStillMatch()
    {
        PasswordEncoder encoder = configuration.passwordEncoder();

        String hash = encoder.encode("correct horse battery staple");

        assertThat(hash).startsWith("{argon2}$argon2id$");
        assertThat(encoder.matches("correct horse battery staple", hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
        assertThat(encoder.upgradeEncoding(hash)).isFalse();

        String legacy = "{bcrypt}" + new BCryptPasswordEncoder().encode("secret-password");
        assertThat(encoder.matches("secret-password", legacy)).isTrue();
        assertThat(encoder.upgradeEncoding(legacy)).isTrue();
    }

    @Test
    void clockIsUtc()
    {
        assertThat(configuration.clock().getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
