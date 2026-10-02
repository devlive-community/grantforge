// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import org.devlive.grantforge.authz.application.RoleInheritanceService;

/**
 * That a role inherits from another; IDs are strings because they exceed JavaScript's safe integers.
 *
 * @param roleId the inheriting role
 * @param parentId the role it inherits from
 */
public record RoleLinkResponse(String roleId, String parentId)
{
    /**
     * Converts a link.
     *
     * @param link the link
     * @return the response
     */
    public static RoleLinkResponse from(RoleInheritanceService.Link link)
    {
        return new RoleLinkResponse(Long.toString(link.roleId()), Long.toString(link.parentId()));
    }
}
