// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.IssuedClient;
import org.devlive.grantforge.authz.application.OAuthClientView;
import org.devlive.grantforge.authz.domain.ClientGrant;
import org.devlive.grantforge.authz.domain.ClientType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClientResponseTest
{
    @Test
    void exposesIdsAsStringsAndSortsSetsButNeverPrintsTheSecret()
    {
        OAuthClientView view = new OAuthClientView(9_007_199_254_740_993L, 2, "gf_a", "CRM", ClientType.CONFIDENTIAL,
                List.of("https://a.example/cb"), Set.of("profile", "openid"), Set.of(ClientGrant.REFRESH_TOKEN, ClientGrant.AUTHORIZATION_CODE),
                Duration.ofMinutes(15), Duration.ofDays(1), true, Instant.EPOCH, null, Instant.EPOCH);

        IssuedClientResponse issued = IssuedClientResponse.from(new IssuedClient(view, "s3cret"));

        assertThat(issued.client()).isEqualTo(new ClientResponse("9007199254740993", "2", "gf_a", "CRM", ClientType.CONFIDENTIAL,
                List.of("https://a.example/cb"), List.of("openid", "profile"), List.of(ClientGrant.AUTHORIZATION_CODE, ClientGrant.REFRESH_TOKEN),
                15, 24, true, Instant.EPOCH, null, Instant.EPOCH));
        assertThat(issued.secret()).isEqualTo("s3cret");
        assertThat(issued.toString()).doesNotContain("s3cret");
    }
}
