// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantCreatedTest
{
    @Test
    void namesTheTenant()
    {
        assertThat(new TenantCreated(7, true)).extracting(TenantCreated::tenantId, TenantCreated::platform).containsExactly(7L, true);
    }
}
