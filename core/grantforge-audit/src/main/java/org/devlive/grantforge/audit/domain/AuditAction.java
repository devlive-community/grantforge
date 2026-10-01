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
    PASSWORD_CHANGED,
    /** A platform administrator created a tenant and its first administrator. */
    TENANT_CREATED,
    /** A platform administrator changed a tenant's details. */
    TENANT_UPDATED,
    /** A platform administrator suspended a tenant, ending its sessions. */
    TENANT_SUSPENDED,
    /** A platform administrator reactivated a tenant. */
    TENANT_ACTIVATED,
    /** An administrator created a department. */
    ORG_UNIT_CREATED,
    /** An administrator renamed a department. */
    ORG_UNIT_UPDATED,
    /** An administrator moved or reordered a department. */
    ORG_UNIT_MOVED,
    /** An administrator deleted a department. */
    ORG_UNIT_DELETED,
    /** An administrator created an account. */
    USER_CREATED,
    /** An administrator changed an account's details or departments. */
    USER_UPDATED,
    /** An administrator enabled an account. */
    USER_ENABLED,
    /** An administrator disabled an account, ending its sessions. */
    USER_DISABLED,
    /** An administrator locked an account, ending its sessions. */
    USER_LOCKED,
    /** An administrator unlocked an account. */
    USER_UNLOCKED,
    /** An administrator set a new password for an account, ending its sessions. */
    USER_PASSWORD_RESET,
    /** An administrator deleted an account. */
    USER_DELETED
}
