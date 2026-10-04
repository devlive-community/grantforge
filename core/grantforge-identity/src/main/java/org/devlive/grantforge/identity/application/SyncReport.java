// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * What a sync of a directory changed.
 *
 * @param found how many users the directory listed
 * @param created accounts created for new users
 * @param updated accounts whose names or e-mail addresses changed
 * @param disabled accounts disabled because their users are gone
 * @param problems users that could not get an account, such as one whose name a local account has (at most 20)
 */
public record SyncReport(int found, int created, int updated, int disabled, List<String> problems)
{
    /** Copies the problems. */
    public SyncReport
    {
        problems = List.copyOf(requireNonNull(problems, "problems"));
    }

    /**
     * Sums the report up in one line, for the audit trail and the console.
     *
     * @return the summary
     */
    public String summary()
    {
        String counts = "found " + found + ", created " + created + ", updated " + updated + ", disabled " + disabled;
        return problems.isEmpty() ? counts : counts + "; " + problems.size() + " skipped: " + String.join(", ", problems);
    }
}
