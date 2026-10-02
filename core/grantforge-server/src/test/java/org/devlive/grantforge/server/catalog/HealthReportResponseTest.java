// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.HealthFinding;
import org.devlive.grantforge.authz.application.HealthIssue;
import org.devlive.grantforge.authz.application.HealthReport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HealthReportResponseTest
{
    @Test
    void idsBecomeStrings()
    {
        HealthReportResponse response = HealthReportResponse.from(new HealthReport(9_007_199_254_740_993L, Instant.EPOCH, List.of(
                new HealthFinding(HealthIssue.DEPENDENCY_ON_DISABLED, 2, "system.user", 3L, "api:x", null, null),
                new HealthFinding(HealthIssue.GRANT_EXPIRED, 4, "system.user.btn.edit", null, null, "acme", "auditors"))));

        assertThat(response.applicationId()).isEqualTo("9007199254740993");
        assertThat(response.checkedAt()).isEqualTo(Instant.EPOCH);
        assertThat(response.findings()).containsExactly(
                new HealthReportResponse.Finding(HealthIssue.DEPENDENCY_ON_DISABLED, "2", "system.user", "3", "api:x", null, null),
                new HealthReportResponse.Finding(HealthIssue.GRANT_EXPIRED, "4", "system.user.btn.edit", null, null, "acme",
                        "auditors"));
    }
}
