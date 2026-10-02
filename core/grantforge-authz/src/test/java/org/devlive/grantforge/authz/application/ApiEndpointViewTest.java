// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.EndpointAccess;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiEndpointViewTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void requiresTheRouteAccessAndLastSighting()
    {
        assertThat(new ApiEndpointView(1, "GET", "/api", "A#b", EndpointAccess.PUBLIC, null, null, true, null, null,
                Instant.EPOCH).active()).isTrue();
        assertThatThrownBy(() -> new ApiEndpointView(1, null, "/api", "A#b", EndpointAccess.PUBLIC, null, null, true, null, null,
                Instant.EPOCH)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ApiEndpointView(1, "GET", null, "A#b", EndpointAccess.PUBLIC, null, null, true, null, null,
                Instant.EPOCH)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ApiEndpointView(1, "GET", "/api", null, EndpointAccess.PUBLIC, null, null, true, null, null,
                Instant.EPOCH)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ApiEndpointView(1, "GET", "/api", "A#b", null, null, null, true, null, null,
                Instant.EPOCH)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ApiEndpointView(1, "GET", "/api", "A#b", EndpointAccess.PUBLIC, null, null, true, null, null,
                null)).isInstanceOf(NullPointerException.class);
    }
}
