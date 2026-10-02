// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import org.devlive.grantforge.service.agent.AccessPage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessPageResponseTest
{
    @Test
    void carriesTheCursor()
    {
        assertThat(AccessPageResponse.from(new AccessPage(List.of(), "abc"))).isEqualTo(new AccessPageResponse(List.of(), "abc"));
    }
}
