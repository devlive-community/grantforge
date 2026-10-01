// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * The outcome of checking, and possibly applying, an import file. An import applies all rows or none: with any
 * problem nothing is written.
 *
 * @param rows how many data rows the file has
 * @param created how many records were created; 0 when nothing was applied
 * @param applied whether the rows were written
 * @param problems the problems found, by row
 */
public record ImportReport(int rows, int created, boolean applied, List<ImportProblem> problems)
{
    /** Copies the problems. */
    public ImportReport
    {
        problems = List.copyOf(requireNonNull(problems, "problems"));
    }
}
