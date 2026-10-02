// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import org.devlive.grantforge.common.error.GrantForgeException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleParentsRequestTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately leaves the list out, as JSON without it does
    void parsesTheIdsAndTreatsAMissingListAsEmpty()
    {
        assertThat(new RoleParentsRequest(List.of(" 7 ", "9007199254740993")).ids()).containsExactly(7L, 9_007_199_254_740_993L);
        assertThat(new RoleParentsRequest(null).ids()).isEmpty();
        assertThatThrownBy(() -> new RoleParentsRequest(List.of("x")).ids()).isInstanceOf(GrantForgeException.class);
    }
}
