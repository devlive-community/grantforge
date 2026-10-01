// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.csv;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvExceptionTest
{
    @Test
    void namesTheLine()
    {
        CsvException error = new CsvException(3, "broken");

        assertThat(error.getLine()).isEqualTo(3);
        assertThat(error).hasMessage("broken (line 3)");
    }
}
