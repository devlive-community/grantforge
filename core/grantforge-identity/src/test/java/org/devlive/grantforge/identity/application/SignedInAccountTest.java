// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignedInAccountTest
{
    @Test
    void exposesItsComponents()
    {
        SignedInAccount account = new SignedInAccount(1, 2, "alice", null, true);

        assertThat(account.accountId()).isEqualTo(1);
        assertThat(account.tenantId()).isEqualTo(2);
        assertThat(account.username()).isEqualTo("alice");
        assertThat(account.displayName()).isNull();
        assertThat(account.passwordChangeRequired()).isTrue();
    }
}
