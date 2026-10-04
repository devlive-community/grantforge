// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

/** Where a round of an access review stands. */
public enum ReviewRoundStatus
{
    /** Reviewers decide its items until it is completed or cancelled. */
    OPEN,

    /** Its decisions were applied: assignments to revoke were removed. */
    COMPLETED,

    /** Ended without applying anything. */
    CANCELLED
}
