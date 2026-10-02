// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.ImpactReport;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What a change would do to the roles it touches; IDs are strings because they exceed JavaScript's safe integers.
 *
 * @param roles the roles whose access would change
 * @param accounts how many accounts hold one of those roles
 * @param gained the codes of the resources some role would gain
 * @param lost the codes of the resources some role would lose
 */
public record ImpactReportResponse(List<AffectedRole> roles, long accounts, List<String> gained, List<String> lost)
{
    /** Copies the lists. */
    public ImpactReportResponse
    {
        roles = List.copyOf(roles);
        gained = List.copyOf(gained);
        lost = List.copyOf(lost);
    }

    /**
     * A role whose access would change.
     *
     * @param roleId the role
     * @param tenantCode the code of its tenant, for changes of the shared catalog
     * @param code its code
     * @param name its name
     * @param gained how many resources it would gain
     * @param lost how many resources it would lose
     */
    public record AffectedRole(String roleId, @Nullable String tenantCode, String code, String name, int gained, int lost)
    {
        static AffectedRole from(ImpactReport.AffectedRole role)
        {
            return new AffectedRole(Long.toString(role.roleId()), role.tenantCode(), role.code(), role.name(), role.gained(),
                    role.lost());
        }
    }

    /**
     * Converts a report.
     *
     * @param report the report
     * @return the response
     */
    public static ImpactReportResponse from(ImpactReport report)
    {
        return new ImpactReportResponse(report.roles().stream().map(AffectedRole::from).toList(), report.accounts(),
                report.gained(), report.lost());
    }
}
