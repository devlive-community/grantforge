// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.application.IdentityErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordChangeGuardTest
{
    private final PasswordChangeGuard guard = new PasswordChangeGuard();

    private boolean passes(MockHttpServletRequest request)
    {
        return guard.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    @Test
    void stopsSessionsThatMustChangeThePassword()
    {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        assertThat(passes(request)).isTrue();

        PasswordChangeGuard.require(session, true);
        assertThatThrownBy(() -> passes(request)).isInstanceOfSatisfying(GrantForgeException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.PASSWORD_CHANGE_REQUIRED));

        PasswordChangeGuard.require(session, false);
        assertThat(passes(request)).isTrue();
        assertThat(passes(new MockHttpServletRequest())).isTrue();
    }
}
