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
 * @param directoryManaged whether a directory owns this department
 * @param dataSourceName the owning data source name, if available
 */
public record OrgUnitResponse(String id, @Nullable String parentId, String code, String name, int sortOrder, int depth,
                              boolean directoryManaged, @Nullable String dataSourceName)
{
    /**
     * Creates a local department response.
     *
     * @param id the source ID
     * @param parentId the parent id
     * @param code the code
     * @param name the name
     * @param sortOrder the sort order
     * @param depth the depth
     */
    public OrgUnitResponse(String id, @Nullable String parentId, String code, String name, int sortOrder, int depth)
    {
        this(id, parentId, code, name, sortOrder, depth, false, null);
    }

    /**
     * Converts a view.
     *
     * @param unit the view
     * @return the response
     */
    public static OrgUnitResponse from(OrgUnitView unit)
    {
        return from(unit, null);
    }

    /**
     * Converts a view with its data source name.
     *
     * @param unit the view
     * @param dataSourceName the owning source name, if known
     * @return the response
     */
    public static OrgUnitResponse from(OrgUnitView unit, @Nullable String dataSourceName)
    {
        Long parent = unit.parentId();
        return new OrgUnitResponse(Long.toString(unit.id()), parent == null ? null : Long.toString(parent), unit.code(),
                unit.name(), unit.sortOrder(), unit.depth(), unit.directoryManaged(), dataSourceName);
    }
}
