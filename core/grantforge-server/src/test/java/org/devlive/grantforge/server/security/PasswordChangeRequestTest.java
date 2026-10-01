// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordChangeRequestTest
{
    @Test
    void keepsThePasswordsOutOfLogs()
    {
        assertThat(new PasswordChangeRequest("old secret", "new secret").toString())
                .doesNotContain("secret").isEqualTo("PasswordChangeRequest[]");
    }
}
