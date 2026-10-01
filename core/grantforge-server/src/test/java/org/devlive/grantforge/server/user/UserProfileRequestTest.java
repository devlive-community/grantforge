// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.user;

import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.application.UserProfileInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileRequestTest
{
    @Test
    @SuppressWarnings("NullAway") // JSON without further departments passes null
    void parsesDepartmentIds()
    {
        assertThat(new UserProfileRequest("Alice", "a@b", " 7 ", List.of("8", " 9")).toInput())
                .isEqualTo(new UserProfileInput("Alice", "a@b", 7L, List.of(8L, 9L)));
        assertThat(new UserProfileRequest(null, null, " ", null).toInput())
                .isEqualTo(new UserProfileInput(null, null, null, List.of()));
        assertThatThrownBy(() -> new UserProfileRequest(null, null, "x", null).toInput())
                .isInstanceOf(GrantForgeException.class);
    }
}
