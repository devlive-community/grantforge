// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.jspecify.annotations.Nullable;

/** A statement Doris refused or failed, or no frontend that could be reached. */
final class DorisFailure
        extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final boolean unreachable;

    DorisFailure(String message, boolean unreachable, @Nullable Throwable cause)
    {
        super(message, cause);
        this.unreachable = unreachable;
    }

    /**
     * Returns whether no frontend could be reached, or the connection to it broke, rather than one refusing.
     *
     * @return {@code true} for a connection failure
     */
    boolean unreachable()
    {
        return unreachable;
    }
}
