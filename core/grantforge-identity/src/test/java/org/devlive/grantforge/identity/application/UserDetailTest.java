// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.identity.domain.AccountStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserDetailTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void copiesTheMemberships()
    {
        UserSummary summary = new UserSummary(1, "alice", null, null, AccountStatus.ACTIVE, null, false, false, null,
                Instant.EPOCH, null, null);
        List<UserMembership> memberships = new ArrayList<>(List.of(new UserMembership(2, "HQ", true)));
        UserDetail detail = new UserDetail(summary, memberships);
        memberships.clear();

        assertThat(detail.memberships()).hasSize(1);
        assertThatThrownBy(() -> new UserDetail(null, List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserDetail(summary, null)).isInstanceOf(NullPointerException.class);
    }
}
