// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.data;

import org.devlive.grantforge.persistence.secured.DataAction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataPreviewRequestTest
{
    @Test
    void namesTheUserEntityAndAction()
    {
        assertThat(new DataPreviewRequest("1", "user", DataAction.READ).entityCode()).isEqualTo("user");
    }
}
