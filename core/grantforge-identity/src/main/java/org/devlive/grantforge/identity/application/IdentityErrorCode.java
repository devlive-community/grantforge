// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.common.error.ErrorCode;

/** Errors of the identity module; messages live in {@code i18n/identity*.properties}. */
public enum IdentityErrorCode
        implements ErrorCode
{
    /** First-run setup already happened and can never run again. */
    SETUP_COMPLETED("GF-IDENTITY-001", 409, "error.identity.setup-completed"),
    /** The setup token is wrong or no longer valid. */
    SETUP_TOKEN_INVALID("GF-IDENTITY-002", 403, "error.identity.setup-token-invalid"),
    /** The password is shorter than the policy allows; argument: the minimum length. */
    PASSWORD_TOO_SHORT("GF-IDENTITY-010", 400, "error.identity.password-too-short"),
    /** The password is longer than the policy allows; argument: the maximum length. */
    PASSWORD_TOO_LONG("GF-IDENTITY-011", 400, "error.identity.password-too-long"),
    /** The password contains the login name. */
    PASSWORD_CONTAINS_USERNAME("GF-IDENTITY-012", 400, "error.identity.password-contains-username"),
    /** The password mixes too few character classes; argument: the required number. */
    PASSWORD_TOO_SIMPLE("GF-IDENTITY-013", 400, "error.identity.password-too-simple"),
    /** The password was used recently; argument: how many recent passwords are remembered. */
    PASSWORD_REUSED("GF-IDENTITY-014", 400, "error.identity.password-reused"),
    /** The current password given to confirm a change is wrong. */
    PASSWORD_INCORRECT("GF-IDENTITY-015", 400, "error.identity.password-incorrect"),
    /** The user must choose a new password before doing anything else. */
    PASSWORD_CHANGE_REQUIRED("GF-IDENTITY-016", 403, "error.identity.password-change-required"),
    /** Unknown login name or wrong password; deliberately does not say which. */
    INVALID_CREDENTIALS("GF-IDENTITY-020", 401, "error.identity.invalid-credentials"),
    /** Too many failed sign-ins; the account is locked for a while. */
    ACCOUNT_LOCKED("GF-IDENTITY-021", 401, "error.identity.account-locked"),
    /** An administrator disabled the account. */
    ACCOUNT_DISABLED("GF-IDENTITY-022", 401, "error.identity.account-disabled"),
    /** The account's tenant is suspended. */
    TENANT_SUSPENDED("GF-IDENTITY-023", 401, "error.identity.tenant-suspended"),
    /** Another tenant already uses the code; argument: the code. */
    TENANT_CODE_TAKEN("GF-IDENTITY-030", 409, "error.identity.tenant-code-taken"),
    /** Another account, in any tenant, already uses the login name; argument: the name. */
    USERNAME_TAKEN("GF-IDENTITY-031", 409, "error.identity.username-taken"),
    /** The platform tenant cannot be suspended. */
    PLATFORM_TENANT_PROTECTED("GF-IDENTITY-032", 409, "error.identity.platform-tenant-protected");

    private final String code;
    private final int httpStatus;
    private final String messageKey;

    IdentityErrorCode(String code, int httpStatus, String messageKey)
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
