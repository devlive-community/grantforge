// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

/** What happens to assignments nobody decided about when a round of an access review completes. */
public enum ReviewFallback
{
    /** They stay. */
    KEEP,

    /** They are removed, as if a reviewer had decided to revoke them. */
    REVOKE
}
