// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserFilterTest
{
    @Test
    void allMeansNoFilter()
    {
        assertThat(UserFilter.ALL).isEqualTo(new UserFilter(null, null, null, false));
    }
}
