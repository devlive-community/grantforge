// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

/** Which rows a data rule covers. */
public enum DataScope
{
    /** Every row. */
    ALL,
    /** Rows of the user's tenant, or every row of an entity without a tenant column. */
    TENANT,
    /** Rows of the user's departments and those below them. */
    ORG_AND_CHILDREN,
    /** Rows of the user's departments. */
    ORG,
    /** Rows of chosen departments. */
    CUSTOM_ORGS,
    /** Rows the user owns. */
    SELF,
    /** Rows a condition on their fields selects. */
    CONDITION
}
