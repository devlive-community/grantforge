// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.yarn;

import org.jspecify.annotations.Nullable;

import java.io.IOException;

/** The lookup user could not sign in to Kerberos. */
final class YarnLoginException
        extends IOException
{
    private static final long serialVersionUID = 1L;

    /**
     * Creates the failure.
     *
     * @param message what went wrong
     * @param cause why, or {@code null}
     */
    YarnLoginException(String message, @Nullable Throwable cause)
    {
        super(message, cause);
    }
}
