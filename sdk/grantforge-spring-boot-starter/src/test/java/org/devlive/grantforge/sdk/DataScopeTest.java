// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataScopeTest
{
    @Test
    void matchesGrantForgesNames()
    {
        assertThat(DataScope.valueOf("ORG_AND_CHILDREN").name()).isEqualTo("ORG_AND_CHILDREN");
        assertThat(DataScope.values()).hasSize(7);
    }
}
