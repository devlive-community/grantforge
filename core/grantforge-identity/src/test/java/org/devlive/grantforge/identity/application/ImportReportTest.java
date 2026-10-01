// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportReportTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void copiesTheProblems()
    {
        List<ImportProblem> problems = new ArrayList<>(List.of(new ImportProblem(2, null, IdentityErrorCode.IMPORT_EMPTY,
                List.of())));
        ImportReport report = new ImportReport(1, 0, false, problems);
        problems.clear();

        assertThat(report.problems()).hasSize(1);
        assertThatThrownBy(() -> new ImportReport(0, 0, false, null)).isInstanceOf(NullPointerException.class);
    }
}
