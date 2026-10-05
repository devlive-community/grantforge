// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.identity;

import org.devlive.grantforge.identity.application.SyncReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SyncReportResponseTest
{
    @Test
    void copiesTheReport()
    {
        assertThat(SyncReportResponse.from(new SyncReport(3, 1, 1, 0, List.of("bob"))))
                .isEqualTo(new SyncReportResponse(3, 1, 1, 0, List.of("bob"), "found 3, created 1, updated 1, disabled 0; 1 skipped: bob"));
    }
}
