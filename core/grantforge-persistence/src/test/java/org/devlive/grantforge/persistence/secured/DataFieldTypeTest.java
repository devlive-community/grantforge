// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataFieldTypeTest
{
    @Test
    void namesTheKindsOfValues()
    {
        assertThat(DataFieldType.values()).containsExactly(DataFieldType.TEXT, DataFieldType.NUMBER, DataFieldType.BOOLEAN,
                DataFieldType.CHOICE, DataFieldType.TIME);
    }
}
