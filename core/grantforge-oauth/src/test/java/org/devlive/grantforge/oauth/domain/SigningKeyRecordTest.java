// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SigningKeyRecordTest
{
    @Test
    void retiresOnce()
    {
        SigningKeyRecord key = SigningKeyRecord.create("kid", "RS256", "pub", "sealed", Instant.EPOCH);

        assertThat(key.getKeyId()).isEqualTo("kid");
        assertThat(key.getAlgorithm()).isEqualTo("RS256");
        assertThat(key.getPublicKey()).isEqualTo("pub");
        assertThat(key.getPrivateKey()).isEqualTo("sealed");
        assertThat(key.getActivatedAt()).isEqualTo(Instant.EPOCH);
        assertThat(key.getRetiredAt()).isNull();
        key.retire(Instant.EPOCH.plusSeconds(5));
        key.retire(Instant.EPOCH.plusSeconds(9));
        assertThat(key.getRetiredAt()).isEqualTo(Instant.EPOCH.plusSeconds(5));
    }
}
