// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserMembershipTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void requiresTheDepartmentName()
    {
        assertThat(new UserMembership(1, "HQ", true).primary()).isTrue();
        assertThatThrownBy(() -> new UserMembership(1, null, true)).isInstanceOf(NullPointerException.class);
    }
}
