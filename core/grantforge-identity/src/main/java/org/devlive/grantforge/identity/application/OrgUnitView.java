// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.identity.domain.OrgUnit;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A department as the console shows it.
 *
 * @param id the department ID
 * @param parentId the parent department, or {@code null} for a root
 * @param code the code, unique in the tenant
 * @param name the display name
 * @param sortOrder the position among its siblings
 * @param depth the level; roots are at 0
 */
public record OrgUnitView(long id, @Nullable Long parentId, String code, String name, int sortOrder, int depth)
{
    /** Validates the values. */
    public OrgUnitView
    {
        requireNonNull(code, "code");
        requireNonNull(name, "name");
    }

    /**
     * Converts a unit.
     *
     * @param unit the unit
     * @return the view
     */
    public static OrgUnitView from(OrgUnit unit)
    {
        return new OrgUnitView(unit.requireId(), unit.getParentId(), unit.getCode(), unit.getName(), unit.getSortOrder(),
                unit.getDepth());
    }
}
