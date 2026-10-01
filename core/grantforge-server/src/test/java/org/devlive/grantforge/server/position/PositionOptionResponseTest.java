// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.position;

import org.devlive.grantforge.identity.application.UserPosition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PositionOptionResponseTest
{
    @Test
    void exposesTheIdAsAString()
    {
        assertThat(PositionOptionResponse.from(new UserPosition(7, "CFO"))).isEqualTo(new PositionOptionResponse("7", "CFO"));
    }
}
