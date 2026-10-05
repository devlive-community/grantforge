// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.RoleType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestOptionTest
{
    @Test
    void exposesItsComponents()
    {
        RequestOption option = new RequestOption(new RoleView(1, "reports", "Reports", null, RoleType.CUSTOM, true), 30, true, false);

        assertThat(option.held()).isTrue();
        assertThat(option.pending()).isFalse();
        assertThat(option.maxDays()).isEqualTo(30);
    }
}
