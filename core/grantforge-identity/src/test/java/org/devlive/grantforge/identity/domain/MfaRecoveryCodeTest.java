// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MfaRecoveryCodeTest
{
    @Test
    void isUsedOnce()
    {
        MfaRecoveryCode code = MfaRecoveryCode.of(7, "hash");
        assertThat(code.getAccountId()).isEqualTo(7);
        assertThat(code.getCodeHash()).isEqualTo("hash");
        assertThat(code.isUsed()).isFalse();

        code.use(Instant.parse("2026-06-01T09:00:00Z"));

        assertThat(code.isUsed()).isTrue();
    }
}
