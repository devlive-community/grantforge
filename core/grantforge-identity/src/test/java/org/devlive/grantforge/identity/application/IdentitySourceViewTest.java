// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.identity.domain.IdentitySourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentitySourceViewTest
{
    @Test
    void exposesItsComponents()
    {
        IdentitySourceView view = new IdentitySourceView(1, "corp", "Corp", IdentitySourceType.LDAP, true, false, null, null, true, 60, null,
                null, 4);

        assertThat(view.code()).isEqualTo("corp");
        assertThat(view.secretSet()).isTrue();
        assertThat(view.accounts()).isEqualTo(4);
    }
}
