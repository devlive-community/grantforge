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
    USER_DELETED,
    /** A visitor created their own account through self-registration. */
    USER_REGISTERED,
    /** An administrator created a user group. */
    GROUP_CREATED,
    /** An administrator changed a user group's details. */
    GROUP_UPDATED,
    /** An administrator deleted a user group. */
    GROUP_DELETED,
    /** An administrator added accounts to a user group; the reason holds how many. */
    GROUP_MEMBERS_ADDED,
    /** An administrator removed accounts from a user group; the reason holds how many. */
    GROUP_MEMBERS_REMOVED,
    /** An administrator created a position. */
    POSITION_CREATED,
    /** An administrator changed a position's details. */
    POSITION_UPDATED,
    /** An administrator deleted a position, which every holder lost. */
    POSITION_DELETED,
    /** An administrator imported accounts from a file; the reason holds how many. */
    USERS_IMPORTED,
    /** An administrator imported departments from a file; the reason holds how many. */
    ORG_UNITS_IMPORTED,
    /** A platform administrator registered an application in the resource catalog. */
    APPLICATION_CREATED,
    /** A platform administrator changed an application's name or description. */
    APPLICATION_UPDATED,
    /** A platform administrator removed an application from the catalog. */
    APPLICATION_DELETED,
    /** A platform administrator added a resource (menu, page, button, API...) to the catalog. */
    RESOURCE_CREATED,
    /** A platform administrator changed a resource. */
    RESOURCE_UPDATED,
    /** A platform administrator moved a resource, with everything below it, or changed its position. */
    RESOURCE_MOVED,
    /** A platform administrator deleted a resource. */
    RESOURCE_DELETED,
    /** A platform administrator confirmed changes of the API catalog; the reason holds how many. */
    API_CHANGES_REVIEWED,
    /** A platform administrator made a resource (the target) depend on another, whose ID the reason holds. */
    RESOURCE_DEPENDENCY_ADDED,
    /** A platform administrator made a dependency of the target resource required or optional. */
    RESOURCE_DEPENDENCY_CHANGED,
    /** A platform administrator removed a dependency of the target resource. */
    RESOURCE_DEPENDENCY_REMOVED,
    /** An administrator created a role; the reason holds its code. */
    ROLE_CREATED,
    /** An administrator changed a role's code, name or description. */
    ROLE_UPDATED,
    /** An administrator created a role as a copy of the target; the reason holds the copy's ID. */
    ROLE_COPIED,
    /** An administrator enabled a role. */
    ROLE_ENABLED,
    /** An administrator disabled a role; it grants nothing until enabled again. */
    ROLE_DISABLED,
    /** An administrator deleted a role. */
    ROLE_DELETED
}
