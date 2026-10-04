// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.identity.application.MfaStatus;

/**
 * Whether the signed-in user signs in in two steps.
 *
 * @param enabled whether a code of the authenticator app is asked at sign-in
 * @param recoveryCodesLeft how many unused recovery codes remain
 */
public record MfaStatusResponse(boolean enabled, int recoveryCodesLeft)
{
    /**
     * Converts a status.
     *
     * @param status the status
     * @return the response
     */
    public static MfaStatusResponse from(MfaStatus status)
    {
        return new MfaStatusResponse(status.enabled(), status.recoveryCodesLeft());
    }
}
