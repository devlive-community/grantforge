// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

/** Where a role comes from. */
public enum RoleType
{
    /** Created by GrantForge for every tenant (see {@link SystemRole}); cannot be changed, disabled or deleted. */
    SYSTEM,

    /** Created by an administrator. */
    CUSTOM
}
