// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.transfer;

import org.devlive.grantforge.identity.application.ImportReport;
import org.devlive.grantforge.server.error.ErrorMessages;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The outcome of checking or applying an import file; with any problem nothing was written.
 *
 * @param rows how many data rows the file has
 * @param created how many records were created
 * @param applied whether the rows were written
 * @param problems the problems found, by row
 */
public record ImportReportResponse(int rows, int created, boolean applied, List<Problem> problems)
{
    /** Copies the problems. */
    public ImportReportResponse
    {
        problems = List.copyOf(problems);
    }

    /**
     * Why one row cannot be imported.
     *
     * @param row the record number in the file, the header being 1
     * @param column the column at fault, if one is
     * @param code the problem's error code, such as {@code GF-IDENTITY-095}
     * @param message the problem in the request's language
     */
    public record Problem(int row, @Nullable String column, String code, String message)
    {
    }

    /**
     * Converts a report, localising its problems.
     *
     * @param report the report
     * @param messages turns error codes into text
     * @return the response
     */
    public static ImportReportResponse from(ImportReport report, ErrorMessages messages)
    {
        return new ImportReportResponse(report.rows(), report.created(), report.applied(), report.problems().stream()
                .map(problem -> new Problem(problem.row(), problem.column(), problem.code().code(),
                        messages.text(problem.code(), problem.arguments())))
                .toList());
    }
}
