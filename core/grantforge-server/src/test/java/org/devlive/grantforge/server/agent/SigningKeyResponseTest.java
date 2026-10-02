// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.service.agent.SigningKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SigningKeyResponseTest
{
    @Test
    void copiesTheKey()
    {
        assertThat(SigningKeyResponse.from(new SigningKey("id", "Ed25519", "AAAA"))).isEqualTo(new SigningKeyResponse("id", "Ed25519", "AAAA"));
    }
}
