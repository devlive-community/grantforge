// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationVersionTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void startsAtOneForItsScope()
    {
        AuthorizationVersion counter = AuthorizationVersion.first("tenant:7", Instant.EPOCH);
        assertThat(counter.getScope()).isEqualTo("tenant:7");
        assertThat(counter.getVersion()).isEqualTo(1);
        assertThatThrownBy(() -> AuthorizationVersion.first(null, Instant.EPOCH)).isInstanceOf(NullPointerException.class);
    }
}
