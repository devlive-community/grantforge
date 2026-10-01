// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SetupResultTest
{
    @Test
    void exposesItsComponents()
    {
        SetupResult result = new SetupResult("default", "admin");

        assertThat(result.tenantCode()).isEqualTo("default");
        assertThat(result.username()).isEqualTo("admin");
    }
}
