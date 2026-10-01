// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationResponseTest
{
    @Test
    void untilRolesExistEverySignedInUserReachesEverything()
    {
        assertThat(AuthorizationResponse.everything()).isEqualTo(new AuthorizationResponse(0, true, List.of()));
    }

    @Test
    void theResourceListIsCopied()
    {
        List<String> resources = new ArrayList<>(List.of("system.user"));
        AuthorizationResponse response = new AuthorizationResponse(1, false, resources);
        resources.add("system.role");

        assertThat(response.resources()).containsExactly("system.user");
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void aMissingResourceListIsRejected()
    {
        assertThatThrownBy(() -> new AuthorizationResponse(1, false, null)).isInstanceOf(NullPointerException.class);
    }
}
