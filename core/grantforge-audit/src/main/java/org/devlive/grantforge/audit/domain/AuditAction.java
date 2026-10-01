// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.domain;

/** What an audit event records. Stored by name, so constants may be added but never renamed. */
public enum AuditAction
{
    /** A console sign-in succeeded. */
    LOGIN_SUCCEEDED,
    /** A console sign-in was refused; the reason holds the error code. */
    LOGIN_FAILED,
    /** Repeated failed sign-ins locked an account. */
    ACCOUNT_LOCKED,
    /** A user signed out. */
    LOGOUT,
    /** A session was ended by its owner, an administrator, the session limit or a password change. */
    SESSION_REVOKED,
    /** A user changed their own password. */
    PASSWORD_CHANGED
}
