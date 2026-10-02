// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.ImpactReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImpactReportResponseTest
{
    @Test
    void idsBecomeStringsAndTheRestIsKept()
    {
        ImpactReportResponse response = ImpactReportResponse.from(new ImpactReport(List.of(new ImpactReport.AffectedRole(
                9_007_199_254_740_993L, "acme", "auditors", "Auditors", 1, 2)), 4, List.of("a"), List.of("b", "c")));

        assertThat(response.roles()).containsExactly(new ImpactReportResponse.AffectedRole("9007199254740993", "acme", "auditors",
                "Auditors", 1, 2));
        assertThat(response.accounts()).isEqualTo(4);
        assertThat(response.gained()).containsExactly("a");
        assertThat(response.lost()).containsExactly("b", "c");
    }
}
