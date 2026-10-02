// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceSpecTest
{
    @Test
    void holdsValuesExclusionAndRecursion()
    {
        ResourceSpec spec = ResourceSpec.of(List.of("/a", "/b"), true, true);
        assertThat(spec.values()).containsExactly("/a", "/b");
        assertThat(spec.excludes()).isTrue();
        assertThat(spec.recursive()).isTrue();
        assertThat(ResourceSpec.of("*").matchesAnything()).isTrue();
        assertThat(ResourceSpec.of(List.of("*"), true, false).matchesAnything()).isFalse();
        assertThatThrownBy(ResourceSpec::of).hasMessageContaining("at least one value");
        assertThatThrownBy(() -> ResourceSpec.of(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
