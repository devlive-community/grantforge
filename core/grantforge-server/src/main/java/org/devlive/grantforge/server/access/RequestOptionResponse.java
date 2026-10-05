// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.access;

import org.devlive.grantforge.authz.application.RequestOption;
import org.devlive.grantforge.server.role.RoleResponse;

/**
 * A role the signed-in user may ask for.
 *
 * @param role the role
 * @param maxDays the longest period one may ask for
 * @param held whether the user holds it already
 * @param pending whether the user asked for it and waits for a decision
 */
public record RequestOptionResponse(RoleResponse role, int maxDays, boolean held, boolean pending)
{
    /**
     * Converts an option.
     *
     * @param option the option
     * @return the response
     */
    public static RequestOptionResponse from(RequestOption option)
    {
        return new RequestOptionResponse(RoleResponse.from(option.role()), option.maxDays(), option.held(), option.pending());
    }
}
