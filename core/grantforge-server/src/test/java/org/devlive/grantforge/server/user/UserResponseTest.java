// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.user;

import org.devlive.grantforge.identity.application.UserDetail;
import org.devlive.grantforge.identity.application.UserMembership;
import org.devlive.grantforge.identity.application.UserPosition;
import org.devlive.grantforge.identity.application.UserSummary;
import org.devlive.grantforge.identity.domain.AccountStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserResponseTest
{
    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");

    @Test
    void exposesIdsAsStrings()
    {
        UserSummary summary = new UserSummary(9_007_199_254_740_993L, "alice", "Alice", null, AccountStatus.ACTIVE, null,
                false, true, NOW, NOW, 7L, "HQ");

        assertThat(UserResponse.from(summary)).isEqualTo(new UserResponse("9007199254740993", "alice", "Alice", null,
                AccountStatus.ACTIVE, null, false, true, NOW, NOW, "7", "HQ"));
        UserSummary noUnit = new UserSummary(1, "bob", null, null, AccountStatus.DISABLED, NOW, false, false, null, NOW,
                null, null);
        assertThat(UserResponse.from(noUnit).primaryUnitId()).isNull();
        assertThat(UserDetailResponse.from(new UserDetail(summary, List.of(new UserMembership(7, "HQ", true)), List.of(new UserPosition(3, "CFO")))).memberships())
                .containsExactly(new UserDetailResponse.Membership("7", "HQ", true));
        assertThat(UserDetailResponse.from(new UserDetail(summary, List.of(), List.of(new UserPosition(3, "CFO")))).positions())
                .containsExactly(new UserDetailResponse.Holding("3", "CFO"));
    }
}
