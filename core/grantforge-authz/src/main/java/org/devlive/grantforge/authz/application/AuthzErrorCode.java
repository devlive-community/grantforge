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
    /** Other resources depend on the resource; argument: how many. */
    RESOURCE_IN_USE("GF-AUTHZ-016", 409, "error.authz.resource-in-use"),
    /** The two resources cannot depend on each other, such as an API on a button or across applications. */
    DEPENDENCY_INVALID("GF-AUTHZ-020", 409, "error.authz.dependency-invalid"),
    /** The dependency would close a cycle. */
    DEPENDENCY_CYCLE("GF-AUTHZ-021", 409, "error.authz.dependency-cycle"),
    /** The resource already depends on the other. */
    DEPENDENCY_EXISTS("GF-AUTHZ-022", 409, "error.authz.dependency-exists"),
    /** Dependencies declared by GrantForge itself cannot be removed by hand. */
    DEPENDENCY_DECLARED("GF-AUTHZ-023", 409, "error.authz.dependency-declared");

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
