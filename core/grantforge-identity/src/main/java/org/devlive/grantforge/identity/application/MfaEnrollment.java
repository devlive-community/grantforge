// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import static java.util.Objects.requireNonNull;

/**
 * An authenticator to set up: the user scans the link (as a QR code) or types the secret, then confirms with a code.
 *
 * @param secret the secret in Base32, as authenticator apps take it
 * @param uri the otpauth:// link that carries the secret, the account and the issuer
 */
public record MfaEnrollment(String secret, String uri)
{
    /** Checks the parts. */
    public MfaEnrollment
    {
        requireNonNull(secret, "secret");
        requireNonNull(uri, "uri");
    }

    /** Hides the secret from logs. */
    @Override
    public String toString()
    {
        return "MfaEnrollment[]";
    }
}
