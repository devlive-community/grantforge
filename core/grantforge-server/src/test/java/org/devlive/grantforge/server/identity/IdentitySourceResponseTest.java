// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.identity;

import org.devlive.grantforge.identity.application.IdentitySourceView;
import org.devlive.grantforge.identity.application.LdapSettings;
import org.devlive.grantforge.identity.domain.IdentitySourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentitySourceResponseTest
{
    @Test
    void namesTheCallbackOfProvidersOnly()
    {
        IdentitySourceView directory = new IdentitySourceView(7, "corp", "Corp", IdentitySourceType.LDAP, true, true,
                LdapSettings.of("ldap://a", "dc=example"), null, true, null, null, null, 2);
        IdentitySourceView provider = new IdentitySourceView(8, "okta", "Okta", IdentitySourceType.OIDC, true, true, null, null, false, null,
                null, null, 0);

        assertThat(IdentitySourceResponse.from(directory)).extracting(IdentitySourceResponse::id, IdentitySourceResponse::callbackPath,
                IdentitySourceResponse::accounts).containsExactly("7", null, 2L);
        assertThat(IdentitySourceResponse.from(provider).callbackPath()).isEqualTo("/api/v1/auth/federated/callback/okta");
    }
}
