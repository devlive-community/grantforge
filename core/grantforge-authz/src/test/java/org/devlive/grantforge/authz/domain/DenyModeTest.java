// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DenyModeTest
{
    @Test
    void namesFitTheDenyModeColumn()
    {
        // Stored by name in the VARCHAR(16) deny_mode column.
        assertThat(DenyMode.values()).extracting(Enum::name).containsExactly("HIDE", "DISABLE");
    }
}
