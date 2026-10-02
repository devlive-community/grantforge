// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PriorityTest
{
    @Test
    void namesBothPriorities()
    {
        assertThat(Priority.valueOf("OVERRIDE")).isEqualTo(Priority.OVERRIDE);
        assertThat(Priority.values()).hasSize(2);
    }
}
