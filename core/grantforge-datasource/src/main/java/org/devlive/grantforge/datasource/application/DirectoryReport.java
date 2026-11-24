// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.application;

import java.util.List;

/**
 * A completed atomic organization sync.
 *
 * @param employees           employees fetched
 * @param departmentsCreated  departments added
 * @param departmentsUpdated  departments changed
 * @param membershipsAdded    memberships added
 * @param membershipsRemoved  memberships removed
 * @param linked              employees linked to login accounts
 * @param unmatched           employees without login accounts
 * @param retainedDepartments absent departments retained for existing grants
 * @param incremental        whether an existing snapshot was used as a baseline
 * @param problems            bounded diagnostics
 */
public record DirectoryReport(int employees, int departmentsCreated, int departmentsUpdated, int membershipsAdded,
                              int membershipsRemoved,
                              int linked, int unmatched, int retainedDepartments, boolean incremental, List<String> problems)
{
    /**
     * Copies diagnostics.
     */
    public DirectoryReport
    {
        problems = List.copyOf(problems);
    }

    DirectoryReport withAdditionalProblems(List<String> additional)
    {
        List<String> combined = java.util.stream.Stream.concat(additional.stream(), problems.stream()).limit(20).toList();
        return new DirectoryReport(employees, departmentsCreated, departmentsUpdated, membershipsAdded, membershipsRemoved,
                linked, unmatched, retainedDepartments, incremental, combined);
    }

    /**
     * Returns a safe persisted summary.
     *
     * @return the counts
     */
    public String summary()
    {
        return (incremental ? "incremental; " : "full; ") + "employees " + employees + ", departments +" + departmentsCreated + "/~" + departmentsUpdated
            + ", memberships +" + membershipsAdded + "/-" + membershipsRemoved + ", linked " + linked + ", unmatched " + unmatched
            + ", retained departments " + retainedDepartments + ", problems " + problems.size();
    }
}
