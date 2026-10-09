// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A lookup failed for a reason the console can name, so it can tell an administrator why instead of showing no values.
 * Thrown by {@link ServiceTypeProvider#lookup(LookupRequest)}. Any other exception a plugin throws is reported as
 * {@link Reason#FAILED}, which keeps plugins built against API 1.0 working unchanged.
 *
 * <p>The message is shown to administrators: keep it to one line and never put secrets in it.
 *
 * @since 1.1.0
 */
public final class LookupException
        extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /** Why the lookup failed. */
    private final Reason reason;

    /**
     * Creates the exception.
     *
     * @param reason why the lookup failed
     * @param message what the console shows; one line without secrets
     */
    public LookupException(Reason reason, String message)
    {
        this(reason, message, null);
    }

    /**
     * Creates the exception with the failure that caused it.
     *
     * @param reason why the lookup failed
     * @param message what the console shows; one line without secrets
     * @param cause the underlying failure, logged by the server but not shown
     */
    public LookupException(Reason reason, String message, @Nullable Throwable cause)
    {
        super(requireNonNull(message, "message"), cause);
        this.reason = requireNonNull(reason, "reason");
    }

    /**
     * Returns why the lookup failed.
     *
     * @return the reason
     */
    public Reason getReason()
    {
        return reason;
    }

    /** Why a lookup failed. */
    public enum Reason
    {
        /** The place to look in, such as the configured lookup directory, does not exist. */
        NOT_FOUND,

        /** The target system refused the lookup user, for example for lack of permission. */
        ACCESS_DENIED,

        /** The target system could not be reached: unknown host, refused or timed out connection. */
        UNREACHABLE,

        /** Signing in to the target system failed, such as wrong Kerberos credentials. */
        AUTHENTICATION_FAILED,

        /** There were more values than the plugin may read; the user should narrow the lookup. */
        LIMIT_EXCEEDED,

        /** What was typed or the context cannot be looked up, such as a path outside the allowed directory. */
        INVALID_INPUT,

        /** Any other failure. */
        FAILED
    }
}
