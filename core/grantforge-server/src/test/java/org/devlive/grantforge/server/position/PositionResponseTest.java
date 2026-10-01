// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.position;

import org.devlive.grantforge.identity.domain.PositionRow;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PositionResponseTest
{
    @Test
    void exposesTheIdAsAString()
    {
        assertThat(PositionResponse.from(new PositionRow(9_007_199_254_740_993L, "cfo", "CFO", null, 2, 3)))
                .isEqualTo(new PositionResponse("9007199254740993", "cfo", "CFO", null, 2, 3));
    }
}
