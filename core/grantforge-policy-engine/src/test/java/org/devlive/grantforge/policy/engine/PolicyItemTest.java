// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyItemTest
{
    @Test
    void needsSomeoneAndAnAccessType()
    {
        PolicyItem item = PolicyItem.builder().users("alice").groups("g").roles("r").accessTypes("read")
                .conditions(Condition.of("t", "v")).build();
        assertThat(item.users()).containsExactly("alice");
        assertThat(item.groups()).containsExactly("g");
        assertThat(item.roles()).containsExactly("r");
        assertThat(item.accessTypes()).containsExactly("read");
        assertThat(item.conditions()).hasSize(1);
        assertThatThrownBy(() -> PolicyItem.builder().accessTypes("read").build()).hasMessageContaining("user, group or role");
        assertThatThrownBy(() -> PolicyItem.builder().users("a").build()).hasMessageContaining("access type");
    }
}
