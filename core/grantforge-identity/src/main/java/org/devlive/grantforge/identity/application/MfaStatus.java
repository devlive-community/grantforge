// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

/**
 * Whether an account signs in in two steps.
 *
 * @param enabled whether a confirmed authenticator is required at sign-in
 * @param recoveryCodesLeft how many unused recovery codes remain
 */
public record MfaStatus(boolean enabled, int recoveryCodesLeft)
{
}
