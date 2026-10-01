// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.group;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberChangeResponseTest
{
    @Test
    void carriesTheNumberOfChangedMemberships()
    {
        assertThat(new MemberChangeResponse(3).changed()).isEqualTo(3);
    }
}
