// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SigningKeyTest
{
    @Test
    void describesTheKey()
    {
        assertThat(new SigningKey("0123456789abcdef", "Ed25519", "AAAA").algorithm()).isEqualTo("Ed25519");
    }
}
