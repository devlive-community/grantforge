// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.org;

import org.devlive.grantforge.identity.application.OrgUnitView;
import org.jspecify.annotations.Nullable;

/**
 * A department; IDs are strings because they exceed JavaScript's safe integers.
 *
 * @param id the department ID
 * @param parentId the parent department, or {@code null} for a root
 * @param code the code, unique in the tenant
 * @param name the display name
 * @param sortOrder the position among its siblings
 * @param depth the level; roots are at 0
 */
public record OrgUnitResponse(String id, @Nullable String parentId, String code, String name, int sortOrder, int depth)
{
    /**
     * Converts a view.
     *
     * @param unit the view
     * @return the response
     */
    public static OrgUnitResponse from(OrgUnitView unit)
    {
        Long parent = unit.parentId();
        return new OrgUnitResponse(Long.toString(unit.id()), parent == null ? null : Long.toString(parent), unit.code(),
                unit.name(), unit.sortOrder(), unit.depth());
    }
}
