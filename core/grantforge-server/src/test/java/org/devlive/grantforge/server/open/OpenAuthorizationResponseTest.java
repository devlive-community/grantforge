// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.open;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAuthorizationResponseTest
{
    @Test
    void copiesItsLists()
    {
        List<String> permissions = new ArrayList<>(List.of("orders.read"));
        OpenAuthorizationResponse response = new OpenAuthorizationResponse("shop", "1", "2", "ada", 7, List.of("sellers"), List.of("shop"),
                permissions, Instant.EPOCH);
        permissions.clear();

        assertThat(response.permissions()).containsExactly("orders.read");
        assertThat(response.roles()).containsExactly("sellers");
    }
}
