// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.transfer;

import org.devlive.grantforge.identity.application.IdentityErrorCode;
import org.devlive.grantforge.identity.application.ImportProblem;
import org.devlive.grantforge.identity.application.ImportReport;
import org.devlive.grantforge.server.error.ErrorMessages;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ImportReportResponseTest
{
    @Test
    void localisesEveryProblem()
    {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage(IdentityErrorCode.IMPORT_UNKNOWN_UNIT.messageKey(), Locale.getDefault(), "no unit {0}");
        ImportReport report = new ImportReport(3, 0, false, List.of(new ImportProblem(2, "primaryUnit",
                IdentityErrorCode.IMPORT_UNKNOWN_UNIT, List.of("hq"))));

        ImportReportResponse response = ImportReportResponse.from(report, new ErrorMessages(source));

        assertThat(response.problems()).containsExactly(new ImportReportResponse.Problem(2, "primaryUnit",
                "GF-IDENTITY-095", "no unit hq"));
        assertThat(response.rows()).isEqualTo(3);
    }
}
