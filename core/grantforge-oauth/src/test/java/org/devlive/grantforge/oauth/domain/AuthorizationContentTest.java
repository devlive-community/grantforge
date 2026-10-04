// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationContentTest
{
    @Test
    void copiesItsScopes()
    {
        AuthorizationContent content = OAuthTestData.signedIn("c", "a", "r");

        assertThat(content.authorizedScopes()).containsExactlyInAnyOrder("openid", "profile");
        assertThatThrownBy(() -> content.accessScopes().add("email")).isInstanceOf(UnsupportedOperationException.class);
    }
}
