// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantCommandTest
{
    @Test
    void keepsThePasswordOutOfLogs()
    {
        assertThat(new TenantCommand("acme", "Acme", "boss", null, "secret password").toString())
                .doesNotContain("secret").contains("acme", "boss");
    }
}
