// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpCodesTest
{
    /** Base32 of the RFC 6238 SHA-1 key "12345678901234567890". */
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void matchesTheRfcTestVectors()
    {
        assertThat(TotpCodes.code(SECRET, Instant.ofEpochSecond(59))).isEqualTo("287082");
        assertThat(TotpCodes.code(SECRET.toLowerCase(java.util.Locale.ROOT), Instant.ofEpochSecond(1111111109))).isEqualTo("081804");
        assertThatThrownBy(() -> TotpCodes.code("1", Instant.EPOCH)).isInstanceOf(IllegalArgumentException.class);
    }
}
