// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

/** How the console shows a button or page the user may not use. */
public enum DenyMode
{
    /** Not shown at all. */
    HIDE,

    /** Shown but disabled, for example so users know the operation exists. */
    DISABLE
}
