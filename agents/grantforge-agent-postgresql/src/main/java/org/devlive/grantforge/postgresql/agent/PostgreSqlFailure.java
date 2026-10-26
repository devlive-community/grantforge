// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.jspecify.annotations.Nullable;

/** A statement PostgreSQL refused or failed, or no server that could be reached. */
final class PostgreSqlFailure
        extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /** PostgreSQL's SQLSTATE for a privilege the user lacks. */
    static final String INSUFFICIENT_PRIVILEGE = "42501";

    private final @Nullable String state;

    PostgreSqlFailure(String message, @Nullable String state, @Nullable Throwable cause)
    {
        super(message, cause);
        this.state = state;
    }

    /**
     * Returns PostgreSQL's code for the failure.
     *
     * @return the SQLSTATE, or {@code null} when there is none
     */
    @Nullable String state()
    {
        return state;
    }

    /**
     * Returns whether no server could be reached, or the connection to it broke, rather than one refusing.
     *
     * @return {@code true} for a connection failure, a server starting or shutting down, or one with no connection left
     */
    boolean unreachable()
    {
        return state == null || state.startsWith("08") || state.startsWith("57P") || "53300".equals(state);
    }
}
