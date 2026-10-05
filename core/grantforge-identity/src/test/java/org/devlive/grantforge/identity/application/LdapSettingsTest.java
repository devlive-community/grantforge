// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.GrantForgeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LdapSettingsTest
{
    @Test
    @SuppressWarnings("NullAway") // blank and missing values from a form take the defaults alike
    void takesTheDefaultsOfOpenLdap()
    {
        LdapSettings settings = new LdapSettings("ldaps://a ldap://b", "dc=example", " ", " ", null, "", "", "", true);

        assertThat(settings.bindDn()).isNull();
        assertThat(settings.userFilter()).isEqualTo(LdapSettings.DEFAULT_FILTER);
        assertThat(settings.usernameAttribute()).isEqualTo("uid");
        assertThat(settings.displayNameAttribute()).isEqualTo("cn");
        assertThat(settings.emailAttribute()).isEqualTo("mail");
        assertThat(settings.idAttribute()).isEqualTo("entryUUID");
        assertThat(settings.disableMissing()).isTrue();
        assertThat(LdapSettings.of("ldap://a", "dc=example").url()).isEqualTo("ldap://a");
    }

    @Test
    void refusesWhatCannotWork()
    {
        for (Runnable invalid : new Runnable[] {
            () -> LdapSettings.of(" ", "dc=example"),
            () -> LdapSettings.of("http://a", "dc=example"),
            () -> LdapSettings.of("ldap://a b", "dc=example"),
            () -> LdapSettings.of("::", "dc=example"),
            () -> LdapSettings.of("ldap://a", ""),
            () -> new LdapSettings("ldap://a", "dc=example", null, "uid={0}", "", "", "", "", false),
            () -> new LdapSettings("ldap://a", "dc=example", null, "(uid=x)", "", "", "", "", false),
        }) {
            assertThatThrownBy(invalid::run).satisfies(error ->
                    assertThat(((GrantForgeException) error).getErrorCode()).isEqualTo(IdentityErrorCode.IDENTITY_SOURCE_INVALID));
        }
    }
}
