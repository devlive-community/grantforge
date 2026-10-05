// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import static java.util.Objects.requireNonNull;

/**
 * A provider the sign-in page offers a button for.
 *
 * @param code the source's code, which the sign-in link names
 * @param name what the button says
 */
public record SignInOption(String code, String name)
{
    /** Checks the values. */
    public SignInOption
    {
        requireNonNull(code, "code");
        requireNonNull(name, "name");
    }
}
