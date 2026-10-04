// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.identity.domain.IdentitySourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentitySourceCommandTest
{
    @Test
    void keepsTheSecretOutOfItsText()
    {
        IdentitySourceCommand command = new IdentitySourceCommand("corp", "Corp", IdentitySourceType.LDAP, true, true,
                LdapSettings.of("ldap://a", "dc=example"), null, "bind-secret", null);

        assertThat(command.secret()).isEqualTo("bind-secret");
        assertThat(command.toString()).isEqualTo("IdentitySourceCommand[corp, LDAP]");
    }
}
