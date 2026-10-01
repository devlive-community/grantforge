// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.api;

/**
 * Result of first-run setup.
 *
 * @param tenantCode code of the created tenant
 * @param username login name of the created administrator, to prefill the sign-in form
 */
public record SetupResponse(String tenantCode, String username)
{
}
