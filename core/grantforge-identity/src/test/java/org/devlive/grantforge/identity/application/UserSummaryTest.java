// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.identity.domain.AccountStatus;
import org.devlive.grantforge.identity.domain.UserRow;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserSummaryTest
{
    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");

    private static UserRow row(@Nullable Instant lockedUntil)
    {
        return new UserRow(1, "alice", "Alice", "a@b", AccountStatus.ACTIVE, lockedUntil, false, true, null, NOW, 2L, "HQ");
    }

    @Test
    void reportsOnlyLocksThatStillHold()
    {
        assertThat(UserSummary.from(row(NOW.plusSeconds(60)), NOW).lockedUntil()).isEqualTo(NOW.plusSeconds(60));
        assertThat(UserSummary.from(row(NOW), NOW).lockedUntil()).isNull();
        assertThat(UserSummary.from(row(null), NOW)).extracting(UserSummary::primaryUnitName, UserSummary::mustChangePassword)
                .containsExactly("HQ", true);
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void requiresTheNameStatusAndCreationTime()
    {
        assertThatThrownBy(() -> new UserSummary(1, null, null, null, AccountStatus.ACTIVE, null, false, false, null, NOW,
                null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserSummary(1, "a", null, null, null, null, false, false, null, NOW, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserSummary(1, "a", null, null, AccountStatus.ACTIVE, null, false, false, null, null,
                null, null)).isInstanceOf(NullPointerException.class);
    }
}
