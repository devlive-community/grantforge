// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaskTypeDefinitionTest
{
    @Test
    void transformersAreOptional()
    {
        assertThat(new MaskTypeDefinition("nullify", "Nullify", " ").transformer()).isNull();
        assertThat(new MaskTypeDefinition("hash", " Hash ", "sha2({col})")).isEqualTo(new MaskTypeDefinition("hash", "Hash",
                "sha2({col})"));
        assertThatThrownBy(() -> new MaskTypeDefinition("Hash", "Hash", null)).hasMessageContaining("mask type name");
        assertThatThrownBy(() -> new MaskTypeDefinition("hash", "", null)).hasMessageContaining("label of mask type hash");
    }
}
