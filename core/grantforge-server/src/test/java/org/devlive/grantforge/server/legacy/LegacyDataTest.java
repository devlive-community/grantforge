// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.legacy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class LegacyDataTest
{
    @Test
    void keepsCopiesOfItsLists()
    {
        List<LegacyData.User> users = new ArrayList<>(List.of(new LegacyData.User(1, "admin", null, true, false, false, null)));
        LegacyData data = new LegacyData(users, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        users.clear();

        assertThat(data.users()).hasSize(1);
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void requiresEveryList()
    {
        assertThatNullPointerException().isThrownBy(() -> new LegacyData(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null));
    }
}
