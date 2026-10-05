// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LdapSettingsRequestTest
{
    @Test
    void appliesTheDefaults()
    {
        assertThat(new LdapSettingsRequest("ldap://a", "dc=example", null, null, null, null, null, "objectGUID", null).settings())
                .satisfies(settings -> {
                    assertThat(settings.usernameAttribute()).isEqualTo("uid");
                    assertThat(settings.idAttribute()).isEqualTo("objectGUID");
                    assertThat(settings.disableMissing()).isFalse();
                });
    }
}
