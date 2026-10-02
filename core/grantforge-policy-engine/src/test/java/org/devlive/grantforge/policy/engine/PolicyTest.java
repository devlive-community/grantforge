// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyTest
{
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @Test
    void appliesWhileEnabledAndWithinAPeriod()
    {
        PolicyItem item = PolicyItem.builder().users("a").accessTypes("read").build();
        Policy policy = Policy.builder(7).priority(Priority.OVERRIDE).resource("path", ResourceSpec.of("/"))
                .allow(item).allowExceptions(item).deny(item).denyExceptions(item).build();
        assertThat(policy.id()).isEqualTo(7);
        assertThat(policy.priority()).isEqualTo(Priority.OVERRIDE);
        assertThat(policy.resources()).containsKey("path");
        assertThat(policy.allow()).hasSize(1);
        assertThat(policy.allowExceptions()).hasSize(1);
        assertThat(policy.deny()).hasSize(1);
        assertThat(policy.denyExceptions()).hasSize(1);
        assertThat(policy.appliesAt(NOW)).isTrue();
        assertThat(Policy.builder(1).resource("path", ResourceSpec.of("/")).enabled(false).build().appliesAt(NOW)).isFalse();
        Policy later = Policy.builder(2).resource("path", ResourceSpec.of("/")).validity(Validity.between(NOW.plusSeconds(1), null))
                .build();
        assertThat(later.appliesAt(NOW)).isFalse();
        assertThatThrownBy(() -> Policy.builder(3).build()).hasMessageContaining("names no resource");
    }
}
