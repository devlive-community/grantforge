// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestableRoleTest
{
    @Test
    void allowsUpToAYear()
    {
        RequestableRole setting = RequestableRole.of(3, 30);

        assertThat(setting.getRoleId()).isEqualTo(3);
        assertThat(setting.getMaxDays()).isEqualTo(30);
        assertThat(RequestableRole.of(3, RequestableRole.MAX_DAYS).getMaxDays()).isEqualTo(365);
        assertThatThrownBy(() -> RequestableRole.of(3, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RequestableRole.of(3, 366)).isInstanceOf(IllegalArgumentException.class);
    }
}
