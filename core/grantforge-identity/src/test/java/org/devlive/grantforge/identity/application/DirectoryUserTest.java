// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectoryUserTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void needsAnIdAndAName()
    {
        DirectoryUser user = new DirectoryUser("1", "alice", null, "a@example.com");

        assertThat(user.username()).isEqualTo("alice");
        assertThat(user.email()).isEqualTo("a@example.com");
        assertThatThrownBy(() -> new DirectoryUser(null, "alice", null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DirectoryUser("1", null, null, null)).isInstanceOf(NullPointerException.class);
    }
}
