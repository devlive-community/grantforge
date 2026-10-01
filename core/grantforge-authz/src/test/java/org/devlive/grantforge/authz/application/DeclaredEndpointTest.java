// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ApiEndpoint;
import org.devlive.grantforge.authz.domain.EndpointAccess;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeclaredEndpointTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void namesItsRoute()
    {
        ApiEndpoint.Declaration open = new ApiEndpoint.Declaration("A#b", EndpointAccess.PUBLIC, null);

        assertThat(new DeclaredEndpoint("GET", "/api/v1/bootstrap", open).route()).isEqualTo("GET /api/v1/bootstrap");
        assertThatThrownBy(() -> new DeclaredEndpoint(null, "/api", open)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DeclaredEndpoint("GET", null, open)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DeclaredEndpoint("GET", "/api", null)).isInstanceOf(NullPointerException.class);
    }
}
