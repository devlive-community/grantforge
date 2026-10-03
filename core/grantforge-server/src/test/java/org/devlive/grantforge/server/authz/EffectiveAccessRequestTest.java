// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveAccessRequestTest
{
    @Test
    void namesTheAccountOrNobody()
    {
        assertThat(new EffectiveAccessRequest("7").accountId()).isEqualTo("7");
        assertThat(new EffectiveAccessRequest(null).accountId()).isNull();
    }
}
