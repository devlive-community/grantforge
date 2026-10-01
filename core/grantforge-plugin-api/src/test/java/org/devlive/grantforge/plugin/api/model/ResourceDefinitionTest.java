// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceDefinitionTest
{
    @Test
    void resourceLevelsUseSensibleDefaults()
    {
        ResourceDefinition level = ResourceDefinition.builder("path").build();

        assertThat(level).isEqualTo(new ResourceDefinition("path", "path", null, MatcherType.WILDCARD, true, true, true,
                false, false, false, Set.of()));
        assertThat(ResourceDefinition.builder("path").label(" Path ").matcher(MatcherType.REGEX).caseSensitive(false)
                .mandatory(false).excludesSupported(false).build())
                .isEqualTo(new ResourceDefinition("path", "Path", null, MatcherType.REGEX, false, false, false, false, false,
                        false, Set.of()));
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void resourceLevelsRefuseMistakes()
    {
        assertThatThrownBy(() -> ResourceDefinition.builder("9lives").build()).hasMessageContaining("resource name");
        assertThatThrownBy(() -> ResourceDefinition.builder("a").parent("a").build()).hasMessageContaining("own parent");
        assertThatThrownBy(() -> ResourceDefinition.builder("a").parent("B").build()).hasMessageContaining("parent of");
        assertThatThrownBy(() -> ResourceDefinition.builder("a").recursiveSupported(true).build())
                .hasMessageContaining("PATH matcher");
        assertThatThrownBy(() -> ResourceDefinition.builder("a").accessTypes("Read").build())
                .hasMessageContaining("access type of resource a");
        assertThatThrownBy(() -> ResourceDefinition.builder("a").label("").build()).hasMessageContaining("label");
        assertThatThrownBy(() -> ResourceDefinition.builder(null)).isInstanceOf(NullPointerException.class);
    }
}
