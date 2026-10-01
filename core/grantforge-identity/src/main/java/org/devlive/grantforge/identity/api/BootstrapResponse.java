// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.api;

/**
 * What the console needs to know before anyone signs in.
 *
 * @param setupRequired whether first-run setup must happen before anything else
 * @param registrationEnabled whether visitors may create their own account
 */
public record BootstrapResponse(boolean setupRequired, boolean registrationEnabled)
{
}
