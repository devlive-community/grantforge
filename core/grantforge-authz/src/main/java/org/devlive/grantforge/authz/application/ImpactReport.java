// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * What a change would do to the roles it touches, worked out before it is made.
 *
 * @param roles the roles whose access would change, with what they would gain and lose
 * @param accounts how many accounts hold one of those roles (another role may still give them what one loses)
 * @param gained the codes of the resources some role would gain
 * @param lost the codes of the resources some role would lose
 */
public record ImpactReport(List<AffectedRole> roles, long accounts, List<String> gained, List<String> lost)
{
    /** Copies the lists. */
    public ImpactReport
    {
        roles = List.copyOf(requireNonNull(roles, "roles"));
        gained = List.copyOf(requireNonNull(gained, "gained"));
        lost = List.copyOf(requireNonNull(lost, "lost"));
    }

    /**
     * A role whose access would change.
     *
     * @param roleId the role
     * @param tenantCode the code of its tenant
     * @param code its code
     * @param name its name
     * @param gained how many resources it would gain
     * @param lost how many resources it would lose
     */
    public record AffectedRole(long roleId, @Nullable String tenantCode, String code, String name, int gained, int lost)
    {
        /** Checks the code and name. */
        public AffectedRole
        {
            requireNonNull(code, "code");
            requireNonNull(name, "name");
        }
    }
}
