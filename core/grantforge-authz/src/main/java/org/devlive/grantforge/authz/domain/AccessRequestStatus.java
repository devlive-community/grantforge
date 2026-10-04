// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

/** Where an access request stands. */
public enum AccessRequestStatus
{
    /** Waiting for an approver. */
    PENDING,

    /** Approved: the requester holds the role until the request's end. */
    APPROVED,

    /** Turned down by an approver. */
    REJECTED,

    /** Withdrawn by the requester before a decision. */
    CANCELLED,

    /** Approved, and the granted period is over: the role was taken back. */
    EXPIRED,

    /** Approved, and ended early by an approver. */
    REVOKED
}
