// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionRowTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void requiresCodeAndName()
    {
        assertThat(new PositionRow(1, "cfo", "CFO", null, 0, 2).holders()).isEqualTo(2);
        assertThatThrownBy(() -> new PositionRow(1, null, "CFO", null, 0, 0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PositionRow(1, "cfo", null, null, 0, 0)).isInstanceOf(NullPointerException.class);
    }
}
