// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

import static java.util.Objects.requireNonNull;

/** Plugin code failed or did not answer in time. */
public final class PluginCallException
        extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /** How the call ended. */
    private final Kind kind;

    /**
     * Creates the exception for a call that failed.
     *
     * @param message what happened
     * @param cause why
     */
    public PluginCallException(String message, Throwable cause)
    {
        this(Kind.FAILED, message, cause);
    }

    /**
     * Creates the exception.
     *
     * @param kind how the call ended
     * @param message what happened
     * @param cause why: what the plugin threw, or the timeout or interrupt
     */
    public PluginCallException(Kind kind, String message, Throwable cause)
    {
        super(message, cause);
        this.kind = requireNonNull(kind, "kind");
    }

    /**
     * Returns how the call ended.
     *
     * @return the kind
     */
    public Kind getKind()
    {
        return kind;
    }

    /** How a plugin call ended without a result. */
    public enum Kind
    {
        /** The plugin threw; the cause is what it threw. */
        FAILED,

        /** The plugin did not answer within the time limit. */
        TIMED_OUT,

        /** The calling thread was interrupted. */
        INTERRUPTED
    }
}
