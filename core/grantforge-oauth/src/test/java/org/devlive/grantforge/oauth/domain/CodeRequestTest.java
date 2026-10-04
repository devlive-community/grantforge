// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CodeRequestTest
{
    @Test
    void copiesTheScopes()
    {
        Set<String> scopes = new HashSet<>(Set.of("openid"));
        CodeRequest request = new CodeRequest("https://id.example/oauth2/authorize", null, scopes, null, null, null, null);
        scopes.add("email");

        assertThat(request.scopes()).containsExactly("openid");
    }
}
