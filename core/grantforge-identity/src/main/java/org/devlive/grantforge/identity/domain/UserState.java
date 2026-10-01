// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

/** What an administrator filters accounts by: whether they can sign in right now and, if not, why. */
public enum UserState
{
    /** Enabled and not locked. */
    ACTIVE,
    /** Disabled by an administrator. */
    DISABLED,
    /** Locked, by an administrator or after failed sign-ins. */
    LOCKED
}
