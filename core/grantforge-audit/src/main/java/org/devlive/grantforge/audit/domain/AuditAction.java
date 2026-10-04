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
    ROLE_DELETED,
    /** An administrator gave the target role to someone; the reason holds the subject, such as {@code USER:42}. */
    ROLE_ASSIGNED,
    /** An administrator changed how long or how widely an assignment of the target role applies. */
    ROLE_ASSIGNMENT_CHANGED,
    /** An administrator took the target role from someone; the reason holds the subject. */
    ROLE_UNASSIGNED,
    /** An administrator changed what the target role allows or denies; the reason holds how many grants changed. */
    ROLE_GRANTS_CHANGED,
    /** An administrator changed which roles the target role inherits from; the reason holds their codes. */
    ROLE_PARENTS_CHANGED,
    /** A platform administrator switched the target plugin on. */
    PLUGIN_ENABLED,
    /** A platform administrator switched the target plugin off. */
    PLUGIN_DISABLED,
    /** A platform administrator had the plugins looked up again; the reason holds how many are active. */
    PLUGINS_RESCANNED,
    /** An administrator added the target service; the reason holds its type. */
    SERVICE_CREATED,
    /** An administrator changed the target service's name, description or configuration. */
    SERVICE_UPDATED,
    /** An administrator removed the target service; the reason holds its name. */
    SERVICE_DELETED,
    /** An administrator added the target policy; the reason holds its service and name. */
    POLICY_CREATED,
    /** An administrator changed the target policy; the reason holds its service and name. */
    POLICY_UPDATED,
    /** An administrator removed the target policy; the reason holds its service and name. */
    POLICY_DELETED,
    /** An administrator issued the target agent token; the reason holds its name. */
    AGENT_TOKEN_ISSUED,
    /** An administrator revoked the target agent token; the reason holds its name. */
    AGENT_TOKEN_REVOKED,
    /** An administrator added the target data policy; the reason holds its role, entity and action. */
    DATA_POLICY_CREATED,
    /** An administrator changed the target data policy; the reason holds its role, entity and action. */
    DATA_POLICY_UPDATED,
    /** An administrator removed the target data policy; the reason holds its role, entity and action. */
    DATA_POLICY_DELETED,
    /** An administrator replaced the field policies of the target role; the reason holds how many fields they cover. */
    FIELD_POLICIES_CHANGED,
    /** A signed-in user called an API without its permission; the target holds the permission, the reason the request. */
    ACCESS_DENIED,
    /** An administrator registered the target OAuth client; the reason holds its client ID. */
    CLIENT_CREATED,
    /** An administrator changed the target OAuth client; the reason holds its client ID. */
    CLIENT_UPDATED,
    /** An administrator gave the target OAuth client a new secret; the reason holds the grace period of the old one. */
    CLIENT_SECRET_ROTATED,
    /** An administrator deleted the target OAuth client; the reason holds its client ID. */
    CLIENT_DELETED,
    /** The actor signed in to the target OAuth client, which received an authorization code; the reason holds the scopes. */
    OAUTH_AUTHORIZED,
    /** A refresh token the target client had already exchanged was presented again; the actor's authorization was revoked. */
    OAUTH_TOKEN_REPLAYED,
    /** The authorization server began signing with a new key; the target is its key ID, the reason why. */
    SIGNING_KEY_ROTATED,
    /** An application declared its data entities and something changed; the target is its code, the reason how many. */
    DATA_ENTITIES_DECLARED,
    /** The actor turned two-step sign-in on with an authenticator app. */
    MFA_ENABLED,
    /** The actor turned two-step sign-in off. */
    MFA_DISABLED,
    /** The actor got new recovery codes; the old ones stopped working. */
    MFA_RECOVERY_CODES_RENEWED,
    /** An administrator turned off two-step sign-in of the target account, as when its authenticator was lost. */
    MFA_RESET,
    /** The actor signed in with a recovery code instead of the authenticator; the reason says how many are left. */
    MFA_RECOVERY_CODE_USED,
    /** The actor confirmed a sensitive operation with a second factor; a failure carries the reason. */
    MFA_STEP_UP
}
