// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceLevelTest
{
    @Test
    void describesALevel()
    {
        ResourceLevel table = ResourceLevel.of("table", "database", MatcherKind.WILDCARD, false);
        assertThat(table.name()).isEqualTo("table");
        assertThat(table.parent()).isEqualTo("database");
        assertThat(table.matcher()).isEqualTo(MatcherKind.WILDCARD);
        assertThat(table.caseSensitive()).isFalse();
        assertThatThrownBy(() -> ResourceLevel.of(" ", null, MatcherKind.EXACT, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
