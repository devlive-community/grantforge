// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.identity;

import org.devlive.grantforge.identity.application.SyncReport;

import java.util.List;

/**
 * What a sync of a directory changed.
 *
 * @param found how many users the directory listed
 * @param created accounts created
 * @param updated accounts whose names changed
 * @param disabled accounts disabled because their users are gone
 * @param problems users that could not get an account
 * @param summary all of it in one line
 */
public record SyncReportResponse(int found, int created, int updated, int disabled, List<String> problems, String summary)
{
    /**
     * Converts a report.
     *
     * @param report the report
     * @return the response
     */
    public static SyncReportResponse from(SyncReport report)
    {
        return new SyncReportResponse(report.found(), report.created(), report.updated(), report.disabled(), report.problems(), report.summary());
    }
}
