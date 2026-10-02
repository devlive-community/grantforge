// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

/** Which policies decide first: override policies before normal ones. */
public enum Priority
{
    /** Decides only when no override policy does. */
    NORMAL,

    /** Decides before any normal policy. */
    OVERRIDE
}
