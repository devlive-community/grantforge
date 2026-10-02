// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessQueryTest
{
    @Test
    void allFiltersNothing()
    {
        assertThat(AccessQuery.ALL).isEqualTo(new AccessQuery(null, null, null, null, null, null));
    }
}
