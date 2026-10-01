// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

/** Administrative state of a user account (temporary lockouts are tracked separately). */
public enum AccountStatus
{
    /** The account can sign in. */
    ACTIVE,
    /** An administrator disabled the account. */
    DISABLED
}
