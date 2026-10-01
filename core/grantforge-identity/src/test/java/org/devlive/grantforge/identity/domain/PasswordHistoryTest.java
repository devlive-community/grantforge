// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordHistoryTest
{
    @Test
    void remembersTheAccountAndHash()
    {
        PasswordHistory entry = PasswordHistory.of(7, "{argon2}x");

        assertThat(entry.getAccountId()).isEqualTo(7);
        assertThat(entry.getPasswordHash()).isEqualTo("{argon2}x");
        assertThatThrownBy(() -> PasswordHistory.of(7, " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
