// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiVersionTest
{
    @Test
    void parsesTwoOrThreeNumbers()
    {
        assertThat(ApiVersion.parse(" 1.2 ")).isEqualTo(new ApiVersion(1, 2, 0));
        assertThat(ApiVersion.parse("1.2.3")).hasToString("1.2.3");
        assertThatThrownBy(() -> ApiVersion.parse("1")).hasMessageContaining("not an API version");
        assertThatThrownBy(() -> ApiVersion.parse("1.2.3.4")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiVersion(1, -1, 0)).hasMessageContaining("negative");
    }

    @Test
    void newerMinorVersionsRunOlderPlugins()
    {
        ApiVersion server = new ApiVersion(1, 4, 2);

        assertThat(server.supports(new ApiVersion(1, 0, 0))).isTrue();
        assertThat(server.supports(new ApiVersion(1, 4, 2))).isTrue();
        assertThat(server.supports(new ApiVersion(1, 4, 3))).as("needs a fix this server lacks").isFalse();
        assertThat(server.supports(new ApiVersion(1, 5, 0))).isFalse();
        assertThat(server.supports(new ApiVersion(2, 0, 0))).isFalse();
        assertThat(new ApiVersion(2, 0, 0).supports(server)).as("a new major version breaks old plugins").isFalse();
        assertThat(ApiVersion.CURRENT.supports(ApiVersion.CURRENT)).isTrue();
    }

    @Test
    void ordersByMajorMinorPatch()
    {
        assertThat(new ApiVersion(1, 9, 9)).isLessThan(new ApiVersion(2, 0, 0));
        assertThat(new ApiVersion(1, 2, 9)).isLessThan(new ApiVersion(1, 3, 0));
        assertThat(new ApiVersion(1, 2, 3)).isLessThan(new ApiVersion(1, 2, 4));
        assertThat(new ApiVersion(1, 2, 3)).isEqualByComparingTo(new ApiVersion(1, 2, 3));
    }
}
