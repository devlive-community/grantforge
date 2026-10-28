// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.application;

import org.devlive.grantforge.common.error.ErrorCode;

/**
 * Safe administrator-facing organization data source errors.
 */
public enum DataSourceErrorCode implements ErrorCode
{
    /**
     * Invalid public settings or directory shape.
     */
    INVALID("GF-DATASOURCE-001", 400, "error.datasource.invalid"),
    /**
     * The SDK could not read a complete authorized directory.
     */
    UNAVAILABLE("GF-DATASOURCE-002", 422, "error.datasource.unavailable"),
    /**
     * A concurrent job or existing directory ownership prevents the change.
     */
    CONFLICT("GF-DATASOURCE-003", 409, "error.datasource.conflict");

    private final String code;
    private final int status;
    private final String key;

    DataSourceErrorCode(String code, int status, String key)
    {
        this.code = code;
        this.status = status;
        this.key = key;
    }

    @Override
    public String code()
    {
        return code;
    }

    @Override
    public int httpStatus()
    {
        return status;
    }

    @Override
    public String messageKey()
    {
        return key;
    }
}
