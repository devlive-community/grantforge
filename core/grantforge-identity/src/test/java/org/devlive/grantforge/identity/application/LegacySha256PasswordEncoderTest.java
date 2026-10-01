// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.lang.Digests;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacySha256PasswordEncoderTest
{
    private final LegacySha256PasswordEncoder encoder = new LegacySha256PasswordEncoder(StandardCharsets.UTF_8);

    @Test
    void matchesUnsaltedHexDigestsInAnyCase()
    {
        String digest = Digests.sha256Hex("secret");

        assertThat(encoder.matches("secret", digest)).isTrue();
        assertThat(encoder.matches("secret", " " + digest.toUpperCase() + " ")).isTrue();
        assertThat(encoder.matches("Secret", digest)).isFalse();
        assertThat(encoder.matches(null, digest)).isFalse();
        assertThat(encoder.matches("secret", null)).isFalse();
        assertThat(encoder.matches("secret", "not-hex")).isFalse();
    }

    @Test
    void usesTheConfiguredCharsetForNonAsciiPasswords()
    {
        Charset gbk = Charset.forName("GBK");
        String digest = HexFormat.of().formatHex(Digests.sha256("密码".getBytes(gbk)));

        assertThat(new LegacySha256PasswordEncoder(gbk).matches("密码", digest)).isTrue();
        assertThat(encoder.matches("密码", digest)).isFalse();
    }

    @Test
    void neverCreatesLegacyHashesAndAlwaysAsksForAnUpgrade()
    {
        assertThatThrownBy(() -> encoder.encode("secret")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(encoder.upgradeEncoding("anything")).isTrue();
    }
}
