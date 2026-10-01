// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.tenant;

import org.devlive.grantforge.identity.application.TenantCommand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantCreateRequestTest
{
    @Test
    void becomesACommandAndKeepsThePasswordOutOfLogs()
    {
        TenantCreateRequest request = new TenantCreateRequest("acme", "Acme", "boss", null, "secret password");

        assertThat(request.toCommand()).isEqualTo(new TenantCommand("acme", "Acme", "boss", null, "secret password"));
        assertThat(request.toString()).doesNotContain("secret").contains("acme", "boss");
    }
}
