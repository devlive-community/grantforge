// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.common.error.ErrorCode;

/** Errors of the authorization module; messages live in {@code i18n/authz*.properties}. */
public enum AuthzErrorCode
        implements ErrorCode
{
    /** Another application already uses the code; argument: the code. */
    APPLICATION_CODE_TAKEN("GF-AUTHZ-001", 409, "error.authz.application-code-taken"),
    /** Applications that are part of GrantForge cannot be deleted. */
    APPLICATION_PROTECTED("GF-AUTHZ-002", 409, "error.authz.application-protected"),
    /** An application that still has resources cannot be deleted. */
    APPLICATION_NOT_EMPTY("GF-AUTHZ-003", 409, "error.authz.application-not-empty"),
    /** Another resource of the application already uses the code; argument: the code. */
    RESOURCE_CODE_TAKEN("GF-AUTHZ-010", 409, "error.authz.resource-code-taken"),
    /** A resource of this type cannot be placed there, such as a button outside a page. */
    RESOURCE_PLACEMENT_INVALID("GF-AUTHZ-011", 409, "error.authz.resource-placement-invalid"),
    /** A resource cannot move below itself or one of its own descendants. */
    RESOURCE_MOVE_CYCLE("GF-AUTHZ-012", 409, "error.authz.resource-move-cycle"),
    /** The tree would nest deeper than allowed; argument: the maximum number of levels. */
    RESOURCE_TOO_DEEP("GF-AUTHZ-013", 409, "error.authz.resource-too-deep"),
    /** A resource with children cannot be deleted. */
    RESOURCE_NOT_EMPTY("GF-AUTHZ-014", 409, "error.authz.resource-not-empty"),
    /** Resources declared by GrantForge itself cannot be deleted or recoded. */
    RESOURCE_PROTECTED("GF-AUTHZ-015", 409, "error.authz.resource-protected"),
    /** Roles grant the resource; argument: how many grants. */
    RESOURCE_GRANTED("GF-AUTHZ-017", 409, "error.authz.resource-granted"),
    /** Other resources depend on the resource; argument: how many. */
    RESOURCE_IN_USE("GF-AUTHZ-016", 409, "error.authz.resource-in-use"),
    /** The two resources cannot depend on each other, such as an API on a button or across applications. */
    DEPENDENCY_INVALID("GF-AUTHZ-020", 409, "error.authz.dependency-invalid"),
    /** The dependency would close a cycle. */
    DEPENDENCY_CYCLE("GF-AUTHZ-021", 409, "error.authz.dependency-cycle"),
    /** The resource already depends on the other. */
    DEPENDENCY_EXISTS("GF-AUTHZ-022", 409, "error.authz.dependency-exists"),
    /** Dependencies declared by GrantForge itself cannot be removed by hand. */
    DEPENDENCY_DECLARED("GF-AUTHZ-023", 409, "error.authz.dependency-declared"),
    /** Another role of the tenant already uses the code; argument: the code. */
    ROLE_CODE_TAKEN("GF-AUTHZ-030", 409, "error.authz.role-code-taken"),
    /** System roles cannot be changed, disabled or deleted. */
    ROLE_PROTECTED("GF-AUTHZ-031", 409, "error.authz.role-protected"),
    /** The subject already has the role. */
    ASSIGNMENT_EXISTS("GF-AUTHZ-032", 409, "error.authz.assignment-exists"),
    /** The end of an assignment's validity is not after its start. */
    ASSIGNMENT_PERIOD_INVALID("GF-AUTHZ-033", 400, "error.authz.assignment-period-invalid"),
    /** The actor may not give this role, such as the platform administrator role outside platform administration. */
    ROLE_NOT_ASSIGNABLE("GF-AUTHZ-034", 403, "error.authz.role-not-assignable"),
    /** A system account keeps its system roles. */
    ASSIGNMENT_PROTECTED("GF-AUTHZ-035", 409, "error.authz.assignment-protected"),
    /** The role allows something the actor has not got, so giving it would escalate the actor's own rights. */
    ROLE_EXCEEDS_ACTOR("GF-AUTHZ-036", 403, "error.authz.role-exceeds-actor"),
    /** Inheriting from the role would make roles inherit from themselves; argument: the role's code. */
    ROLE_INHERITANCE_CYCLE("GF-AUTHZ-037", 409, "error.authz.role-inheritance-cycle"),
    /** Resources of this type cannot be granted (modules are implied, data entities and fields come later). */
    GRANT_TYPE_UNSUPPORTED("GF-AUTHZ-040", 409, "error.authz.grant-type-unsupported"),
    /** Platform resources can only be granted in the platform tenant. */
    GRANT_NOT_ALLOWED("GF-AUTHZ-041", 403, "error.authz.grant-not-allowed"),
    /** The actor may only allow what the actor has; argument: the resource code. */
    GRANT_EXCEEDS_ACTOR("GF-AUTHZ-042", 403, "error.authz.grant-exceeds-actor"),
    /** Some parts of a data policy are wrong; the field issues say which. */
    DATA_POLICY_INVALID("GF-AUTHZ-050", 400, "error.authz.data-policy-invalid"),
    /** Some of a role's field policies are wrong; the field issues say which. */
    FIELD_POLICY_INVALID("GF-AUTHZ-051", 400, "error.authz.field-policy-invalid"),
    /** Some data entities an application declares are wrong; the field issues say which. */
    DATA_ENTITIES_INVALID("GF-AUTHZ-052", 400, "error.authz.data-entities-invalid"),
    /** Some settings of an OAuth client are wrong; the field issues say which. */
    CLIENT_INVALID("GF-AUTHZ-060", 400, "error.authz.client-invalid");

    private final String code;
    private final int httpStatus;
    private final String messageKey;

    AuthzErrorCode(String code, int httpStatus, String messageKey)
    {
        this.code = code;
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    @Override
    public String code()
    {
        return code;
    }

    @Override
    public int httpStatus()
    {
        return httpStatus;
    }

    @Override
    public String messageKey()
    {
        return messageKey;
    }
}
