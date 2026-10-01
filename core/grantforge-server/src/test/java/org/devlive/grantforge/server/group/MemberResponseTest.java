// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.group;

import org.devlive.grantforge.identity.domain.MemberRow;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MemberResponseTest
{
    @Test
    void keepsMissingNamesEmpty()
    {
        assertThat(MemberResponse.from(new MemberRow(1, "bob", null, null, Instant.EPOCH)).displayName()).isNull();
    }
}
