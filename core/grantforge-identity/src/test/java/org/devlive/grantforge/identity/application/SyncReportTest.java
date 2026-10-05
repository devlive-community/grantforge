// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SyncReportTest
{
    @Test
    void sumsItselfUp()
    {
        assertThat(new SyncReport(5, 2, 1, 0, List.of()).summary()).isEqualTo("found 5, created 2, updated 1, disabled 0");
        assertThat(new SyncReport(5, 2, 1, 1, List.of("bob")).summary()).isEqualTo("found 5, created 2, updated 1, disabled 1; 1 skipped: bob");
    }
}
