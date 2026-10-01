// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.group;

import org.devlive.grantforge.identity.domain.GroupMemberRow;
import org.devlive.grantforge.identity.domain.GroupRow;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GroupResponseTest
{
    @Test
    void exposesIdsAsStrings()
    {
        Instant now = Instant.parse("2026-10-01T08:00:00Z");

        assertThat(GroupResponse.from(new GroupRow(9_007_199_254_740_993L, "ops", "Ops", null, 2, now)))
                .isEqualTo(new GroupResponse("9007199254740993", "ops", "Ops", null, 2, now));
        assertThat(GroupMemberResponse.from(new GroupMemberRow(7, "alice", "Alice", null, now)))
                .isEqualTo(new GroupMemberResponse("7", "alice", "Alice", null, now));
    }
}
