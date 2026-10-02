// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.data;

import org.devlive.grantforge.authz.data.DataPreview;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataPreviewResponseTest
{
    @Test
    void copiesTheCounts()
    {
        assertThat(DataPreviewResponse.from(new DataPreview(3, 1))).isEqualTo(new DataPreviewResponse(3, 1));
    }
}
