// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.devlive.grantforge.common.error.ErrorCode;

/** Errors of field permissions; messages live in {@code i18n/persistence*.properties}. */
public enum FieldErrorCode
        implements ErrorCode
{
    /** A request changes fields the writer may not change; the field issues name them. */
    READONLY_CHANGED("GF-FIELD-001", 403, "error.field.readonly-changed");

    private final String code;
    private final int httpStatus;
    private final String messageKey;

    FieldErrorCode(String code, int httpStatus, String messageKey)
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
