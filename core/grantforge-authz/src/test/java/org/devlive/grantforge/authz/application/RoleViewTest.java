// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.RoleType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleViewTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void requiresCodeNameAndType()
    {
        assertThat(new RoleView(1, "x", "X", null, RoleType.CUSTOM, true).enabled()).isTrue();
        assertThatThrownBy(() -> new RoleView(1, null, "X", null, RoleType.CUSTOM, true)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RoleView(1, "x", null, null, RoleType.CUSTOM, true)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RoleView(1, "x", "X", null, null, true)).isInstanceOf(NullPointerException.class);
    }
}
