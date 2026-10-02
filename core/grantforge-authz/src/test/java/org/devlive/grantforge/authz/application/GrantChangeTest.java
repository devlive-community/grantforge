// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.GrantEffect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GrantChangeTest
{
    @Test
    void aMissingEffectTakesTheGrantBack()
    {
        assertThat(new GrantChange(1, null, null).effect()).isNull();
        assertThat(new GrantChange(1, GrantEffect.DENY, null).effect()).isEqualTo(GrantEffect.DENY);
    }
}
