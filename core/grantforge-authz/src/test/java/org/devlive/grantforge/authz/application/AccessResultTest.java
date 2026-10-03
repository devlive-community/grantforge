// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessResultTest
{
    @Test
    void tellsWhetherSomethingIsAllowed()
    {
        assertThat(new AccessResult(AccessKind.RESOURCE, "system.user", true).allowed()).isTrue();
    }
}
