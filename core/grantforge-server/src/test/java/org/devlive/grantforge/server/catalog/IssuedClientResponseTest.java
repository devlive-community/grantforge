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

class IssuedClientResponseTest
{
    @Test
    void carriesTheSecretButNeverPrintsIt()
    {
        OAuthClientView view = new OAuthClientView(1, 2, "gf_a", "SPA", ClientType.PUBLIC, List.of(), Set.of(),
                Set.of(ClientGrant.AUTHORIZATION_CODE), Duration.ofMinutes(15), Duration.ofDays(30), true, null, null, Instant.EPOCH);

        assertThat(IssuedClientResponse.from(new IssuedClient(view, null)).secret()).isNull();
        IssuedClientResponse issued = IssuedClientResponse.from(new IssuedClient(view, "s3cret"));
        assertThat(issued.secret()).isEqualTo("s3cret");
        assertThat(issued.client().clientId()).isEqualTo("gf_a");
        assertThat(issued.toString()).doesNotContain("s3cret");
    }
}
