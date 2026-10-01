// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.application;

import static java.util.Objects.requireNonNull;

/**
 * The origin of the request the current thread handles, so code deep in a call (such as sign-in checks) can
 * audit it without passing it along. The server binds it for every request.
 */
public final class AuditContext
{
    private static final ThreadLocal<RequestOrigin> CURRENT = new ThreadLocal<>();

    private AuditContext()
    {
    }

    /** Restores the previous origin when closed. */
    @FunctionalInterface
    public interface Scope
            extends AutoCloseable
    {
        @Override
        void close();
    }

    /**
     * Binds an origin to the current thread until the returned scope is closed.
     *
     * @param origin the origin
     * @return the scope; close it in a {@code finally} block (try-with-resources)
     */
    public static Scope bind(RequestOrigin origin)
    {
        requireNonNull(origin, "origin");
        RequestOrigin previous = CURRENT.get();
        CURRENT.set(origin);
        return () -> {
            if (previous == null) {
                CURRENT.remove();
            }
            else {
                CURRENT.set(previous);
            }
        };
    }

    /**
     * Returns the origin bound to the current thread.
     *
     * @return the origin, or {@link RequestOrigin#NONE} outside a request
     */
    public static RequestOrigin current()
    {
        RequestOrigin origin = CURRENT.get();
        return origin == null ? RequestOrigin.NONE : origin;
    }
}
