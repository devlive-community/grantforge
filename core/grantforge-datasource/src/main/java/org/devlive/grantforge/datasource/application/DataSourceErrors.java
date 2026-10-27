// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.application;

import org.devlive.grantforge.common.error.GrantForgeException;

/**
 * Public, credential-free data source errors.
 */
public final class DataSourceErrors
{
    private DataSourceErrors()
    {
    }

    /**
     * Rejects invalid configuration or snapshots.
     *
     * @param detail the safe reason
     * @return the error
     */
    public static GrantForgeException invalid(String detail)
    {
        return new GrantForgeException(DataSourceErrorCode.INVALID, detail, detail);
    }

    /**
     * Rejects concurrent or unsafe authority changes.
     *
     * @param detail the safe reason
     * @return the error
     */
    public static GrantForgeException conflict(String detail)
    {
        return new GrantForgeException(DataSourceErrorCode.CONFLICT, detail, detail);
    }

    /**
     * Rejects failed remote directory reads.
     *
     * @param detail the safe reason
     * @return the error
     */
    public static GrantForgeException unavailable(String detail)
    {
        return new GrantForgeException(DataSourceErrorCode.UNAVAILABLE, detail, detail);
    }
}
