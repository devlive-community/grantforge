// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamesTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void checksNamesAndLabels()
    {
        assertThat(Names.name("a-b_1", "thing")).isEqualTo("a-b_1");
        assertThatThrownBy(() -> Names.name(null, "thing")).hasMessageStartingWith("thing must be");
        assertThatThrownBy(() -> Names.name("a".repeat(65), "thing")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Names.label(" A ", "label")).isEqualTo("A");
        assertThat(Names.optional(null)).isNull();
        assertThat(Names.optional(" x ")).isEqualTo("x");
    }
}
