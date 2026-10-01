// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

/** How strongly a resource needs another. */
public enum DependencyKind
{
    /** It does not work without it: granting the resource also grants this one. */
    REQUIRED,

    /** It works better with it; administrators are told, nothing is granted automatically. */
    OPTIONAL
}
