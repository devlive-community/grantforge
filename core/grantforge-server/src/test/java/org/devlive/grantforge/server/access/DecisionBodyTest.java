// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.access;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionBodyTest
{
    @Test
    void mayLeaveEverythingOut()
    {
        assertThat(new DecisionBody(null, null).days()).isNull();
        assertThat(new DecisionBody(3, "ok").comment()).isEqualTo("ok");
    }
}
