// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataPreviewTest
{
    @Test
    void comparesTheRoleAloneWithNow()
    {
        assertThat(new DataPreview(3, 1)).extracting(DataPreview::withRole, DataPreview::now).containsExactly(3L, 1L);
    }
}
