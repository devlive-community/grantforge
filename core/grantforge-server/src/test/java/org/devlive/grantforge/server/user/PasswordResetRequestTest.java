// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetRequestTest
{
    @Test
    void keepsThePasswordOutOfLogs()
    {
        assertThat(new PasswordResetRequest("secret password").toString()).isEqualTo("PasswordResetRequest[]");
    }
}
