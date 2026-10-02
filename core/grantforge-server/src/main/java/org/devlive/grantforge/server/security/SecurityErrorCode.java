// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.common.error.ErrorCode;

/** Errors raised by the security layer; messages live in {@code i18n/messages*.properties}. */
public enum SecurityErrorCode
        implements ErrorCode
{
    /** The CSRF token is missing or stale, typically because the page was open across a sign-in or restart. */
    CSRF_REJECTED("GF-SECURITY-001", 403, "error.security.csrf-rejected"),

    /** The caller lacks the permission the API requires. */
    PERMISSION_DENIED("GF-SECURITY-002", 403, "error.security.permission-denied");

    private final String code;
    private final int httpStatus;
    private final String messageKey;

    SecurityErrorCode(String code, int httpStatus, String messageKey)
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
