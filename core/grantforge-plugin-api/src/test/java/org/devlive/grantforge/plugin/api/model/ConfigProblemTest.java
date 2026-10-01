// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigProblemTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void namesTheFieldAndTheReason()
    {
        assertThat(ConfigProblem.of("host", ConfigProblem.Reason.REQUIRED).detail()).isNull();
        assertThat(new ConfigProblem("host", ConfigProblem.Reason.INVALID, "unreachable").detail()).isEqualTo("unreachable");
        assertThatThrownBy(() -> ConfigProblem.of("host", null)).isInstanceOf(NullPointerException.class);
    }
}
