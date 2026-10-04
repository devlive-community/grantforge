// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ClientGrant;
import org.devlive.grantforge.authz.domain.ClientType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IssuedClientTest
{
    @Test
    void neverPrintsTheSecret()
    {
        OAuthClientView view = new OAuthClientView(1, 2, "gf_a", "CRM", ClientType.CONFIDENTIAL, List.of(), Set.of(),
                Set.of(ClientGrant.CLIENT_CREDENTIALS), Duration.ofMinutes(15), Duration.ofDays(30), true, Instant.EPOCH, null, Instant.EPOCH);

        IssuedClient issued = new IssuedClient(view, "s3cret");

        assertThat(issued.secret()).isEqualTo("s3cret");
        assertThat(issued.toString()).contains("gf_a").doesNotContain("s3cret");
    }
}
