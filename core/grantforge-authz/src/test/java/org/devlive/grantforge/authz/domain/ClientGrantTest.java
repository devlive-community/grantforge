// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientGrantTest
{
    @Test
    void grantsHaveTheirOAuthNames()
    {
        assertThat(ClientGrant.AUTHORIZATION_CODE.oauthName()).isEqualTo("authorization_code");
        assertThat(ClientGrant.REFRESH_TOKEN.oauthName()).isEqualTo("refresh_token");
        assertThat(ClientGrant.CLIENT_CREDENTIALS.oauthName()).isEqualTo("client_credentials");
    }
}
