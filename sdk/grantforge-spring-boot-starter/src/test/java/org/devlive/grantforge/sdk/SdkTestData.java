// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Answers for the SDK tests. */
final class SdkTestData
{
    static final String BODY = """
            {"application": "shop", "accountId": "42", "tenantId": "3", "username": "ada", "version": 7,
             "roles": ["sellers"], "resources": ["shop", "shop.orders"], "permissions": ["orders.read"],
             "computedAt": "2026-10-04T00:00:00Z", "futureField": true}
            """;

    private SdkTestData()
    {
    }

    static UserAuthorization ada()
    {
        return new UserAuthorization("shop", "42", "3", "ada", 7, List.of("sellers"), Set.of("shop", "shop.orders"), Set.of("orders.read"),
                Instant.parse("2026-10-04T00:00:00Z"));
    }
}
