// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HealthReportTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void copiesItsFindings()
    {
        List<HealthFinding> findings = new ArrayList<>();
        HealthReport report = new HealthReport(1, Instant.EPOCH, findings);
        findings.add(new HealthFinding(HealthIssue.UNUSED_API, 1, "api:x", null, null, null, null));

        assertThat(report.findings()).isEmpty();
        assertThatThrownBy(() -> new HealthReport(1, null, List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HealthReport(1, Instant.EPOCH, null)).isInstanceOf(NullPointerException.class);
    }
}
