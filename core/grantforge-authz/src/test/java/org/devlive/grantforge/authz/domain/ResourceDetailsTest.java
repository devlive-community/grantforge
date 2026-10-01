// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceDetailsTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void requiresNameAndDenyMode()
    {
        assertThat(new ResourceDetails("Users", null, "/admin/users", true, true, DenyMode.HIDE).route()).isEqualTo("/admin/users");
        assertThatThrownBy(() -> new ResourceDetails(null, null, null, true, true, DenyMode.HIDE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ResourceDetails("Users", null, null, true, true, null))
                .isInstanceOf(NullPointerException.class);
    }
}
