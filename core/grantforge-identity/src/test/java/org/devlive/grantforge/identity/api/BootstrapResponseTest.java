// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapResponseTest
{
    @Test
    void exposesItsComponents()
    {
        BootstrapResponse response = new BootstrapResponse(true, false);

        assertThat(response.setupRequired()).isTrue();
        assertThat(response.registrationEnabled()).isFalse();
    }
}
