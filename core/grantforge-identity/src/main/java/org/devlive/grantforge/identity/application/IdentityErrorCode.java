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
    PASSWORD_CONTAINS_USERNAME("GF-IDENTITY-012", 400, "error.identity.password-contains-username");

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
