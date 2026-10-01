// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.ApiEndpointView;
import org.devlive.grantforge.authz.domain.EndpointAccess;
import org.devlive.grantforge.authz.domain.EndpointChange;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ApiEndpointResponseTest
{
    @Test
    void exposesIdsAsStrings()
    {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");

        assertThat(ApiEndpointResponse.from(new ApiEndpointView(9_007_199_254_740_993L, "GET", "/api/v1/users", "U#list",
                EndpointAccess.PERMISSION, "system.user.read", 7L, true, EndpointChange.ADDED, now, now)))
                .isEqualTo(new ApiEndpointResponse("9007199254740993", "GET", "/api/v1/users", "U#list", EndpointAccess.PERMISSION,
                        "system.user.read", "7", true, EndpointChange.ADDED, now, now));
        assertThat(ApiEndpointResponse.from(new ApiEndpointView(1, "GET", "/api/v1/me", "M#me", EndpointAccess.AUTHENTICATED,
                null, null, true, null, null, now)).resourceId()).isNull();
    }
}
