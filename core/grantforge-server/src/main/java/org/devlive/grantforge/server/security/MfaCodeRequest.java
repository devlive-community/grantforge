// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * A second factor: a code of the authenticator app or a recovery code.
 *
 * @param code the code as entered
 */
public record MfaCodeRequest(@NotBlank @Size(max = 64) @Nullable String code)
{
    /** Hides the code from logs. */
    @Override
    public String toString()
    {
        return "MfaCodeRequest[]";
    }
}
