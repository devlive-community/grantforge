// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpTest
{
    /** The SHA-1 key of RFC 6238's test vectors. */
    private static final byte[] KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesTheRfcTestVectors()
    {
        // RFC 6238 lists eight digits; six are their last six.
        assertThat(Totp.code(KEY, Totp.step(Instant.ofEpochSecond(59)))).isEqualTo("287082");
        assertThat(Totp.code(KEY, Totp.step(Instant.ofEpochSecond(1111111109)))).isEqualTo("081804");
        assertThat(Totp.code(KEY, Totp.step(Instant.ofEpochSecond(2000000000)))).isEqualTo("279037");
    }

    @Test
    void acceptsTheStepsNextToTheCurrentOne()
    {
        Instant now = Instant.ofEpochSecond(1111111109);
        long step = Totp.step(now);

        assertThat(Totp.verify(KEY, Totp.code(KEY, step), now)).hasValue(step);
        assertThat(Totp.verify(KEY, Totp.code(KEY, step - 1), now)).hasValue(step - 1);
        assertThat(Totp.verify(KEY, "081 804", now)).hasValue(step);
        assertThat(Totp.verify(KEY, Totp.code(KEY, step + 2), now)).isEmpty();
        assertThat(Totp.verify(KEY, "12345", now)).isEmpty();
        assertThat(Totp.verify(KEY, "abcdef", now)).isEmpty();
    }

    @Test
    void base32RoundTrips()
    {
        assertThat(Totp.base32(KEY)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.fromBase32("gezd gnbv gy3t qojq gezd gnbv gy3t qojq")).isEqualTo(KEY);
        assertThat(Totp.base32(new byte[] {(byte) 0xff})).isEqualTo("74");
        assertThat(Totp.fromBase32("74")).containsExactly((byte) 0xff);
        assertThatThrownBy(() -> Totp.fromBase32("1")).isInstanceOf(IllegalArgumentException.class);
    }
}
