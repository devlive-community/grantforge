// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTypeDefinitionTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void accessTypesCannotImplyThemselves()
    {
        assertThat(AccessTypeDefinition.of("all", " All ", "read").label()).isEqualTo("All");
        assertThatThrownBy(() -> AccessTypeDefinition.of("all", "All", "all")).hasMessageContaining("implies itself");
        assertThatThrownBy(() -> AccessTypeDefinition.of("all", "All", "READ")).hasMessageContaining("implied by all");
        assertThatThrownBy(() -> AccessTypeDefinition.of("", "All")).hasMessageContaining("access type name");
        assertThatThrownBy(() -> AccessTypeDefinition.of("all", null)).hasMessageContaining("label");
    }
}
