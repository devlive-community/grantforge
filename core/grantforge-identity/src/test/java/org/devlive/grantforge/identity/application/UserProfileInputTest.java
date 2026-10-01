// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileInputTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void copiesTheFurtherDepartments()
    {
        List<Long> others = new ArrayList<>(List.of(2L));
        UserProfileInput input = new UserProfileInput("Alice", null, 1L, others, List.of(5L));
        others.add(3L);

        assertThat(input).extracting(UserProfileInput::primaryUnitId, UserProfileInput::otherUnitIds,
                UserProfileInput::positionIds).containsExactly(1L, List.of(2L), List.of(5L));
        assertThatThrownBy(() -> new UserProfileInput(null, null, null, null, List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserProfileInput(null, null, null, List.of(), null)).isInstanceOf(NullPointerException.class);
    }
}
