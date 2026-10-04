// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.identity.application.MfaEnrollment;

/**
 * An authenticator to set up: added to the app by its link (as a QR code) or by typing the secret.
 *
 * @param secret the secret in Base32
 * @param uri the otpauth:// link
 */
public record MfaEnrollmentResponse(String secret, String uri)
{
    /**
     * Converts an enrollment.
     *
     * @param enrollment the enrollment
     * @return the response
     */
    public static MfaEnrollmentResponse from(MfaEnrollment enrollment)
    {
        return new MfaEnrollmentResponse(enrollment.secret(), enrollment.uri());
    }

    /** Hides the secret from logs. */
    @Override
    public String toString()
    {
        return "MfaEnrollmentResponse[]";
    }
}
