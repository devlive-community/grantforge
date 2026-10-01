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
    /** An administrator locked the account until it is unlocked. */
    ACCOUNT_LOCKED_BY_ADMINISTRATOR("GF-IDENTITY-024", 401, "error.identity.account-locked-by-administrator"),
    /** Another tenant already uses the code; argument: the code. */
    TENANT_CODE_TAKEN("GF-IDENTITY-030", 409, "error.identity.tenant-code-taken"),
    /** Another account, in any tenant, already uses the login name; argument: the name. */
    USERNAME_TAKEN("GF-IDENTITY-031", 409, "error.identity.username-taken"),
    /** The platform tenant cannot be suspended. */
    PLATFORM_TENANT_PROTECTED("GF-IDENTITY-032", 409, "error.identity.platform-tenant-protected"),
    /** Another department of the tenant already uses the code; argument: the code. */
    ORG_CODE_TAKEN("GF-IDENTITY-040", 409, "error.identity.org-code-taken"),
    /** A department cannot move below itself or one of its own sub-departments. */
    ORG_MOVE_CYCLE("GF-IDENTITY-041", 409, "error.identity.org-move-cycle"),
    /** The tree would nest deeper than allowed; argument: the maximum number of levels. */
    ORG_TOO_DEEP("GF-IDENTITY-042", 409, "error.identity.org-too-deep"),
    /** A department with sub-departments cannot be deleted. */
    ORG_NOT_EMPTY("GF-IDENTITY-043", 409, "error.identity.org-not-empty"),
    /** A department that still has members cannot be deleted. */
    ORG_HAS_MEMBERS("GF-IDENTITY-044", 409, "error.identity.org-has-members"),
    /** System accounts and the administrator's own account cannot be disabled, locked or deleted this way. */
    ACCOUNT_PROTECTED("GF-IDENTITY-050", 409, "error.identity.account-protected"),
    /** Self-registration is switched off, or the tenant it registers into is missing or suspended. */
    REGISTRATION_CLOSED("GF-IDENTITY-060", 403, "error.identity.registration-closed"),
    /** Another user group of the tenant already uses the code; argument: the code. */
    GROUP_CODE_TAKEN("GF-IDENTITY-070", 409, "error.identity.group-code-taken"),
    /** Another position of the tenant already uses the code; argument: the code. */
    POSITION_CODE_TAKEN("GF-IDENTITY-080", 409, "error.identity.position-code-taken"),
    /** The import file is not valid CSV; argument: the line of the problem. */
    IMPORT_MALFORMED("GF-IDENTITY-090", 400, "error.identity.import-malformed"),
    /** The import file lacks a required column; argument: the column. */
    IMPORT_MISSING_COLUMN("GF-IDENTITY-091", 400, "error.identity.import-missing-column"),
    /** The import file has more rows than one import accepts; argument: the maximum. */
    IMPORT_TOO_MANY_ROWS("GF-IDENTITY-092", 400, "error.identity.import-too-many-rows"),
    /** The import file has a header but no data rows. */
    IMPORT_EMPTY("GF-IDENTITY-093", 400, "error.identity.import-empty"),
    /** A value that must be unique appears twice in the import file; argument: the value. */
    IMPORT_DUPLICATE("GF-IDENTITY-094", 400, "error.identity.import-duplicate"),
    /** An import row names a department that does not exist; argument: the department code. */
    IMPORT_UNKNOWN_UNIT("GF-IDENTITY-095", 400, "error.identity.import-unknown-unit"),
    /** An import row names a position that does not exist; argument: the position code. */
    IMPORT_UNKNOWN_POSITION("GF-IDENTITY-096", 400, "error.identity.import-unknown-position"),
    /** An import row has an invalid value; argument: the column. */
    IMPORT_INVALID_VALUE("GF-IDENTITY-097", 400, "error.identity.import-invalid-value"),
    /** An import row leaves a required value empty; argument: the column. */
    IMPORT_REQUIRED_VALUE("GF-IDENTITY-098", 400, "error.identity.import-required-value");

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
