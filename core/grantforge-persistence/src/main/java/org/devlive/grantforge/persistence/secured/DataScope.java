// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

/** Which rows of an entity a data permission covers. */
public enum DataScope
{
    /** Every row of every tenant; only for platform administration. */
    ALL,
    /** Every row of the tenant. */
    TENANT,
    /** Rows of the holder's departments and their sub-departments. */
    ORG_AND_CHILDREN,
    /** Rows of the holder's departments. */
    ORG,
    /** Rows of chosen departments. */
    CUSTOM_ORGS,
    /** Rows that belong to the holder. */
    SELF,
    /** Rows a condition on their fields selects. */
    CONDITION
}
