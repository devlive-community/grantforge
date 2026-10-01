// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupRowTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void requiresCodeNameAndCreationTime()
    {
        assertThat(new GroupRow(1, "ops", "Ops", null, 3, Instant.EPOCH).members()).isEqualTo(3);
        assertThatThrownBy(() -> new GroupRow(1, null, "Ops", null, 0, Instant.EPOCH)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GroupRow(1, "ops", null, null, 0, Instant.EPOCH)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GroupRow(1, "ops", "Ops", null, 0, null)).isInstanceOf(NullPointerException.class);
    }
}
