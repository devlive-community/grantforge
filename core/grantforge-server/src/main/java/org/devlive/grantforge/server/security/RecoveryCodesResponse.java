// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Recovery codes, shown this once: each signs in once instead of the authenticator app.
 *
 * @param codes the codes
 */
public record RecoveryCodesResponse(List<String> codes)
{
    /** Copies the codes. */
    public RecoveryCodesResponse
    {
        codes = List.copyOf(requireNonNull(codes, "codes"));
    }

    /** Hides the codes from logs. */
    @Override
    public String toString()
    {
        return "RecoveryCodesResponse[" + codes.size() + "]";
    }
}
