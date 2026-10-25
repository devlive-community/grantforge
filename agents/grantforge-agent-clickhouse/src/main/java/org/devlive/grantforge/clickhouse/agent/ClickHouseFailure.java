// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.jspecify.annotations.Nullable;

/** A request ClickHouse failed, with ClickHouse's error code, or one that reached no server. */
final class ClickHouseFailure
        extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /** The code of a request that reached no server. */
    static final int UNREACHABLE = -1;

    /** ClickHouse's code for a request the user lacks a privilege for. */
    static final int ACCESS_DENIED = 497;

    private final int code;

    ClickHouseFailure(int code, String message, @Nullable Throwable cause)
    {
        super(message, cause);
        this.code = code;
    }

    /**
     * Returns ClickHouse's error code.
     *
     * @return the code, or {@link #UNREACHABLE}
     */
    int code()
    {
        return code;
    }
}
