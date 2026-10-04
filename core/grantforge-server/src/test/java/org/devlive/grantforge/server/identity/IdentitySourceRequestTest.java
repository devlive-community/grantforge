// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.identity;

import org.devlive.grantforge.identity.application.IdentitySourceCommand;
import org.devlive.grantforge.identity.domain.IdentitySourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentitySourceRequestTest
{
    @Test
    void takesOnlyTheSettingsOfItsType()
    {
        LdapSettingsRequest ldap = new LdapSettingsRequest("ldap://a", "dc=example", null, null, null, null, null, null, true);
        OidcSettingsRequest oidc = new OidcSettingsRequest("https://login.example.com", "c", null, null, null, null);

        IdentitySourceCommand directory = new IdentitySourceRequest(" corp ", "Corp", IdentitySourceType.LDAP, null, false, ldap, oidc, "s", 60)
                .command();
        assertThat(directory.code()).isEqualTo("corp");
        assertThat(directory.enabled()).isTrue();
        assertThat(directory.provisioning()).isFalse();
        assertThat(directory.ldap()).isNotNull().extracting(settings -> settings.disableMissing()).isEqualTo(true);
        assertThat(directory.oidc()).isNull();

        IdentitySourceCommand provider = new IdentitySourceRequest(null, "Okta", IdentitySourceType.OIDC, false, null, ldap, oidc, null, null)
                .command();
        assertThat(provider.code()).isEmpty();
        assertThat(provider.enabled()).isFalse();
        assertThat(provider.ldap()).isNull();
        assertThat(provider.oidc()).isNotNull();
        assertThat(new IdentitySourceRequest("x", "X", null, null, null, null, null, null, null).command().type()).isEqualTo(IdentitySourceType.LDAP);
        assertThat(new IdentitySourceRequest("x", "X", null, null, null, null, null, "s", null).toString()).doesNotContain("s,");
    }
}
