// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceValuesTest
{
    @Test
    void tidiesTheValues()
    {
        assertThat(new ResourceValues(List.of(" sales ", "sales", " "), true, false).values()).containsExactly("sales");
        assertThat(ResourceValues.of("a", "b")).isEqualTo(new ResourceValues(List.of("a", "b"), false, false));
    }
}
