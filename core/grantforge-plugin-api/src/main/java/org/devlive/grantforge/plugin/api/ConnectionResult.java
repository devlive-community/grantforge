// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api;

import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * The outcome of testing a service's connection.
 *
 * @param status whether the target system was reached
 * @param message what the console shows, such as the server's error; {@code null} when there is nothing to add.
 *        Must not contain secrets
 */
public record ConnectionResult(Status status, @Nullable String message)
{
    /** Checks the status. */
    public ConnectionResult
    {
        requireNonNull(status, "status");
    }

    /**
     * Reports success.
     *
     * @return the result
     */
    public static ConnectionResult succeeded()
    {
        return new ConnectionResult(Status.SUCCEEDED, null);
    }

    /**
     * Reports a failure.
     *
     * @param message why
     * @return the result
     */
    public static ConnectionResult failed(String message)
    {
        return new ConnectionResult(Status.FAILED, requireNonNull(message, "message"));
    }

    /**
     * Reports that the plugin cannot test connections.
     *
     * @return the result
     */
    public static ConnectionResult unsupported()
    {
        return new ConnectionResult(Status.UNSUPPORTED, null);
    }

    /** Whether the target system was reached. */
    public enum Status
    {
        /** It was. */
        SUCCEEDED,

        /** It was not; see the message. */
        FAILED,

        /** The plugin does not test connections. */
        UNSUPPORTED
    }
}
