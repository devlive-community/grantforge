// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.group;

import org.devlive.grantforge.identity.domain.GroupMemberRow;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GroupMemberResponseTest
{
    @Test
    void keepsMissingNamesEmpty()
    {
        assertThat(GroupMemberResponse.from(new GroupMemberRow(1, "bob", null, null, Instant.EPOCH)).displayName()).isNull();
    }
}
