// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.oauth.application;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCallerTest
{
    @Test
    void readsPermissionsOnlyWithTheirScope()
    {
        assertThat(new OpenCaller("gf_a", 1, null, Set.of("openid", "permissions")).mayReadPermissions()).isTrue();
        assertThat(new OpenCaller("gf_a", 1, null, Set.of("openid")).mayReadPermissions()).isFalse();
    }
}
