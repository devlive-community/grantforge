// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionUserTest
{
    @Test
    void theAccountIdIsThePrincipalName()
    {
        SessionUser user = new SessionUser(7, 3, " alice ");

        assertThat(user.getName()).isEqualTo("7");
        assertThat(user.username()).isEqualTo("alice");
    }

    @Test
    void identifiersMustBeValid()
    {
        assertThatThrownBy(() -> new SessionUser(0, 3, "a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SessionUser(7, 0, "a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SessionUser(7, 3, " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
