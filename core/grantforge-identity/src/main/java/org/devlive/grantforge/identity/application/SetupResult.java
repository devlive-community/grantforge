// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

/**
 * Outcome of first-run setup.
 *
 * @param tenantCode code of the created tenant
 * @param username login name of the created administrator
 */
public record SetupResult(String tenantCode, String username)
{
}
