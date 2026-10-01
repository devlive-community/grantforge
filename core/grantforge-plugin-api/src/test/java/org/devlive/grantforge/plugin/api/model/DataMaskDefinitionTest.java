// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataMaskDefinitionTest
{
    private static final MaskTypeDefinition NULLIFY = new MaskTypeDefinition("nullify", "Nullify", null);

    @Test
    void needsResourcesAndUniqueMasks()
    {
        assertThat(new DataMaskDefinition(Set.of("column"), List.of(NULLIFY)).maskTypes()).containsExactly(NULLIFY);
        assertThatThrownBy(() -> new DataMaskDefinition(Set.of(), List.of(NULLIFY))).hasMessageContaining("at least one");
        assertThatThrownBy(() -> new DataMaskDefinition(Set.of("column"), List.of())).hasMessageContaining("at least one");
        assertThatThrownBy(() -> new DataMaskDefinition(Set.of("column"), List.of(NULLIFY, NULLIFY)))
                .hasMessageContaining("nullify declared twice");
    }
}
