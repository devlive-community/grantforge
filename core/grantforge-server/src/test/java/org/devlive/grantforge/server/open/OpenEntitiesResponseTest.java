// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.open;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenEntitiesResponseTest
{
    @Test
    void saysHowManyEntitiesTheApplicationHas()
    {
        assertThat(new OpenEntitiesResponse(3).declared()).isEqualTo(3);
    }
}
