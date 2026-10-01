// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RowFilterDefinitionTest
{
    @Test
    void needsAResource()
    {
        assertThat(new RowFilterDefinition(Set.of("table")).resources()).containsExactly("table");
        assertThatThrownBy(() -> new RowFilterDefinition(Set.of())).hasMessageContaining("at least one");
    }
}
