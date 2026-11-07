// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.jspecify.annotations.Nullable;

/** A command Redis refused or failed, or a node that could not be reached. */
final class RedisFailure
        extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final boolean unreachable;

    RedisFailure(String message, boolean unreachable, @Nullable Throwable cause)
    {
        super(message, cause);
        this.unreachable = unreachable;
    }

    /**
     * Returns whether the node could not be reached, or the connection to it broke, rather than one refusing.
     *
     * @return {@code true} for a connection failure
     */
    boolean unreachable()
    {
        return unreachable;
    }
}
