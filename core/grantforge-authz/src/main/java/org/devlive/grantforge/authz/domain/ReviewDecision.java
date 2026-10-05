// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

/** What a reviewer decided about an assignment in an access review round. */
public enum ReviewDecision
{
    /** Nobody decided yet; the review's fallback applies when the round completes. */
    PENDING,

    /** The subject keeps the role. */
    KEEP,

    /** The role is taken from the subject when the round completes. */
    REVOKE
}
