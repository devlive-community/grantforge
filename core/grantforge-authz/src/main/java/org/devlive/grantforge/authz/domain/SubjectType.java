// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

/** Who a role can be given to. */
public enum SubjectType
{
    /** One account. */
    USER,

    /** Every member of a user group. */
    GROUP,

    /** Every member of a department, and of its sub-departments when the assignment includes them. */
    ORG_UNIT,

    /** Every holder of a position. */
    POSITION
}
