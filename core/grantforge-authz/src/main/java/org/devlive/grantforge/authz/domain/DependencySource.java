// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

/** Where a dependency comes from. */
public enum DependencySource
{
    /** Declared by GrantForge's own code (the console's permission manifest); kept in step at start-up. */
    DECLARED,

    /** Added by an administrator. */
    MANUAL
}
