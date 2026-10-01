// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SetupPropertiesTest
{
    @Test
    void blankTokenMeansNone()
    {
        assertThat(new SetupProperties(null).token()).isNull();
        assertThat(new SetupProperties("  ").token()).isNull();
        assertThat(new SetupProperties(" 0123456789abcdef ").token()).isEqualTo("0123456789abcdef");
    }

    @Test
    void shortTokensAreRejected()
    {
        assertThatThrownBy(() -> new SetupProperties("too-short")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("16");
    }
}
