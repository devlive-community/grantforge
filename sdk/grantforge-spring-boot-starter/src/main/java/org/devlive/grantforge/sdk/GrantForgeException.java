// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.jspecify.annotations.Nullable;

import java.io.Serial;

import static java.util.Objects.requireNonNull;

/** Why GrantForge could not, or would not, allow something. */
public final class GrantForgeException
        extends RuntimeException
{
    @Serial
    private static final long serialVersionUID = 1L;

    private final Reason reason;

    /**
     * Creates the exception.
     *
     * @param reason why
     * @param message what happened, for logs
     * @param cause the underlying failure, or {@code null}
     */
    public GrantForgeException(Reason reason, String message, @Nullable Throwable cause)
    {
        super(message, cause);
        this.reason = requireNonNull(reason, "reason");
    }

    /**
     * Returns why.
     *
     * @return the reason
     */
    public Reason getReason()
    {
        return reason;
    }

    /** Why GrantForge did not allow something, with the HTTP status an application answers. */
    public enum Reason
    {
        /** No token, or GrantForge no longer accepts it: the user must sign in again. */
        UNAUTHENTICATED(401),
        /** The user lacks a permission, or the token lacks the {@code permissions} scope. */
        FORBIDDEN(403),
        /** GrantForge did not answer, or answered with an error: nothing is allowed meanwhile. */
        UNAVAILABLE(503);

        private final int status;

        Reason(int status)
        {
            this.status = status;
        }

        /**
         * Returns the HTTP status to answer with.
         *
         * @return the status
         */
        public int status()
        {
            return status;
        }
    }
}
