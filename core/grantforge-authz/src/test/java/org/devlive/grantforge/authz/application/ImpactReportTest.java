// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImpactReportTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void copiesItsListsAndNeedsItsValues()
    {
        List<String> lost = new ArrayList<>(List.of("system.user"));
        ImpactReport report = new ImpactReport(List.of(new ImpactReport.AffectedRole(1, null, "auditors", "Auditors", 0, 1)), 3,
                List.of(), lost);
        lost.clear();

        assertThat(report.lost()).containsExactly("system.user");
        assertThat(report.roles()).extracting(ImpactReport.AffectedRole::code).containsExactly("auditors");
        assertThatThrownBy(() -> new ImpactReport(null, 0, List.of(), List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ImpactReport.AffectedRole(1, "acme", null, "x", 0, 0)).isInstanceOf(NullPointerException.class);
    }
}
