// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.access;

import org.devlive.grantforge.authz.application.RequestableRoleView;
import org.devlive.grantforge.server.role.RoleResponse;

/**
 * A role that may be asked for.
 *
 * @param role the role
 * @param maxDays the longest period one may ask for
 */
public record RequestableRoleResponse(RoleResponse role, int maxDays)
{
    /**
     * Converts a view.
     *
     * @param view the view
     * @return the response
     */
    public static RequestableRoleResponse from(RequestableRoleView view)
    {
        return new RequestableRoleResponse(RoleResponse.from(view.role()), view.maxDays());
    }
}
