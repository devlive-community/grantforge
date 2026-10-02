// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiEndpointTest
{
    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final ApiEndpoint.Declaration READ = new ApiEndpoint.Declaration("UserController#list",
            EndpointAccess.PERMISSION, "system.user.read");

    @Test
    void newEndpointsAreMarkedAdded()
    {
        ApiEndpoint endpoint = ApiEndpoint.discover("get", "/api/v1/users", READ, 7L, T0);

        assertThat(endpoint.getHttpMethod()).isEqualTo("GET");
        assertThat(endpoint.getPathPattern()).isEqualTo("/api/v1/users");
        assertThat(endpoint.getHandler()).isEqualTo("UserController#list");
        assertThat(endpoint.getAccess()).isEqualTo(EndpointAccess.PERMISSION);
        assertThat(endpoint.getPermission()).isEqualTo("system.user.read");
        assertThat(endpoint.getResourceId()).isEqualTo(7L);
        assertThat(endpoint.isActive()).isTrue();
        assertThat(endpoint.getChange()).isEqualTo(EndpointChange.ADDED);
        assertThat(endpoint.getChangedAt()).isEqualTo(T0);
        assertThat(endpoint.getLastSeenAt()).isEqualTo(T0);
    }

    @Test
    void onlyAccessChangesAndReturnsMarkAnEndpointChanged()
    {
        ApiEndpoint endpoint = ApiEndpoint.discover("GET", "/api/v1/users", READ, 7L, T0);
        endpoint.reviewed();
        assertThat(endpoint.getChange()).isNull();
        assertThat(endpoint.getChangedAt()).isNull();

        // A renamed handler alone is no change worth reviewing.
        assertThat(endpoint.seen(new ApiEndpoint.Declaration("UserController#search", EndpointAccess.PERMISSION,
                "system.user.read"), 7L, T1)).isFalse();
        assertThat(endpoint.getHandler()).isEqualTo("UserController#search");
        assertThat(endpoint.getLastSeenAt()).isEqualTo(T1);
        assertThat(endpoint.getChange()).isNull();

        assertThat(endpoint.seen(new ApiEndpoint.Declaration("UserController#search", EndpointAccess.AUTHENTICATED, null),
                null, T1)).isTrue();
        assertThat(endpoint.getChange()).isEqualTo(EndpointChange.CHANGED);
        assertThat(endpoint.getResourceId()).isNull();
        endpoint.reviewed();

        assertThat(endpoint.vanish(T1)).isTrue();
        assertThat(endpoint.vanish(T1)).isFalse();
        assertThat(endpoint.isActive()).isFalse();
        assertThat(endpoint.getChange()).isEqualTo(EndpointChange.REMOVED);
        assertThat(endpoint.seen(READ, 7L, T1)).isTrue();
        assertThat(endpoint.isActive()).isTrue();
        assertThat(endpoint.getChange()).isEqualTo(EndpointChange.CHANGED);
    }

    @Test
    void unreviewedAdditionsStayAdditions()
    {
        ApiEndpoint endpoint = ApiEndpoint.discover("GET", "/api/v1/users", READ, 7L, T0);

        assertThat(endpoint.seen(new ApiEndpoint.Declaration("UserController#list", EndpointAccess.PUBLIC, null), null, T1))
                .isFalse();
        assertThat(endpoint.getChange()).isEqualTo(EndpointChange.ADDED);
    }

    @Test
    void permissionsComeExactlyWithPermissionAccess()
    {
        assertThatThrownBy(() -> new ApiEndpoint.Declaration("A#b", EndpointAccess.PERMISSION, null))
                .hasMessageContaining("A#b");
        assertThatThrownBy(() -> new ApiEndpoint.Declaration("A#b", EndpointAccess.PUBLIC, "system.user.read"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApiEndpoint.discover(" ", "/api", READ, null, T0)).hasMessageContaining("httpMethod");
    }
}
