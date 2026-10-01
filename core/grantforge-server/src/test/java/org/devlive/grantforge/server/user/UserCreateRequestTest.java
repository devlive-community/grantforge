// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserCreateRequestTest
{
    @Test
    void keepsThePasswordOutOfLogs()
    {
        assertThat(new UserCreateRequest("alice", "secret password", null).toString())
                .doesNotContain("secret").contains("alice");
    }
}
