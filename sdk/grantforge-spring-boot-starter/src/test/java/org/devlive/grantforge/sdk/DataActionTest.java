// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataActionTest
{
    @Test
    void matchesGrantForgesNames()
    {
        assertThat(DataAction.valueOf("EXPORT").name()).isEqualTo("EXPORT");
        assertThat(DataAction.values()).hasSize(4);
    }
}
