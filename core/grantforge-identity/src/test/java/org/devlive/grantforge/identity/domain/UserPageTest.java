// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserPageTest
{
    @Test
    void keepsACopyOfItsRows()
    {
        UserRow alice = new UserRow(1, "alice", null, null, AccountStatus.ACTIVE, null, false, false, null, Instant.EPOCH, null, null);
        List<UserRow> rows = new ArrayList<>(List.of(alice));
        UserPage page = new UserPage(rows, 7);
        rows.clear();

        assertThat(page.rows()).containsExactly(alice);
        assertThat(page.total()).isEqualTo(7);
    }
}
