// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.oauth;

import org.devlive.grantforge.oauth.application.SigningKeyView;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthSigningKeyResponseTest
{
    @Test
    void saysWhetherTheKeySigns()
    {
        Instant retired = Instant.EPOCH.plusSeconds(60);

        assertThat(OAuthSigningKeyResponse.from(new SigningKeyView("k", "RS256", Instant.EPOCH, retired, retired.plusSeconds(60))))
                .isEqualTo(new OAuthSigningKeyResponse("k", "RS256", Instant.EPOCH, retired, retired.plusSeconds(60), false));
    }
}
