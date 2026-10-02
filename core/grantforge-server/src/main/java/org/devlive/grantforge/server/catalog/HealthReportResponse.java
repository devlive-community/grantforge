// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.HealthFinding;
import org.devlive.grantforge.authz.application.HealthIssue;
import org.devlive.grantforge.authz.application.HealthReport;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * The result of a catalog check-up; IDs are strings because they exceed JavaScript's safe integers.
 *
 * @param applicationId the application checked
 * @param checkedAt when
 * @param findings what is inconsistent, by issue and resource code
 */
public record HealthReportResponse(String applicationId, Instant checkedAt, List<Finding> findings)
{
    /** Copies the findings. */
    public HealthReportResponse
    {
        findings = List.copyOf(findings);
    }

    /**
     * One inconsistency.
     *
     * @param issue what is wrong
     * @param resourceId the resource concerned
     * @param resourceCode its code
     * @param relatedId for a dependency, the resource depended on
     * @param relatedCode its code
     * @param tenantCode for a grant, the tenant whose role holds it
     * @param roleCode for a grant, the role holding it
     */
    public record Finding(HealthIssue issue, String resourceId, String resourceCode, @Nullable String relatedId,
            @Nullable String relatedCode, @Nullable String tenantCode, @Nullable String roleCode)
    {
        static Finding from(HealthFinding finding)
        {
            Long related = finding.relatedId();
            return new Finding(finding.issue(), Long.toString(finding.resourceId()), finding.resourceCode(),
                    related == null ? null : Long.toString(related), finding.relatedCode(), finding.tenantCode(), finding.roleCode());
        }
    }

    /**
     * Converts a report.
     *
     * @param report the report
     * @return the response
     */
    public static HealthReportResponse from(HealthReport report)
    {
        return new HealthReportResponse(Long.toString(report.applicationId()), report.checkedAt(),
                report.findings().stream().map(Finding::from).toList());
    }
}
