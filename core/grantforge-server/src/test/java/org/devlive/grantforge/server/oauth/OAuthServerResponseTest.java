// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.oauth;

import org.devlive.grantforge.oauth.application.SigningKeyView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthServerResponseTest
{
    @Test
    void copiesTheKeys()
    {
        List<OAuthSigningKeyResponse> keys = new ArrayList<>(List.of(OAuthSigningKeyResponse.from(new SigningKeyView("k", "RS256",
                Instant.EPOCH, null, null))));
        OAuthServerResponse server = new OAuthServerResponse("https://id.example", "https://id.example/.well-known/openid-configuration", keys);
        keys.clear();

        assertThat(server.keys()).singleElement().satisfies(key -> assertThat(key.active()).isTrue());
    }
}
