// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.user;

import org.devlive.grantforge.identity.application.UserDetail;
import org.devlive.grantforge.identity.application.UserSummary;
import org.devlive.grantforge.identity.domain.AccountStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserDetailResponseTest
{
    @Test
    void anAccountWithoutDepartmentsHasNoMemberships()
    {
        UserSummary summary = new UserSummary(1, "bob", null, null, AccountStatus.ACTIVE, null, false, false, null,
                Instant.EPOCH, null, null);

        assertThat(UserDetailResponse.from(new UserDetail(summary, List.of())).memberships()).isEmpty();
    }
}
