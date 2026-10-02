// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.host;

/** Plugin code failed or did not answer in time. */
public final class PluginCallException
        extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what happened
     * @param cause why
     */
    public PluginCallException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
