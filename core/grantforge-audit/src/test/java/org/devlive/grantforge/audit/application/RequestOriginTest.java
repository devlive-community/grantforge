// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestOriginTest
{
    @Test
    void theOriginOutsideRequestsIsEmpty()
    {
        assertThat(RequestOrigin.NONE).isEqualTo(new RequestOrigin(null, null, null));
    }
}
