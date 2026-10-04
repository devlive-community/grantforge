// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAccessEndedTest
{
    @Test
    void namesTheClient()
    {
        assertThat(new ClientAccessEnded(4, "gf_a").clientId()).isEqualTo("gf_a");
    }
}
