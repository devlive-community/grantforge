// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

/**
 * Published once a tenant exists (after its transaction committed), so other modules can prepare what every
 * tenant has, such as its built-in roles.
 *
 * @param tenantId the new tenant
 * @param platform whether it is the platform tenant (created by first-run setup)
 */
public record TenantCreated(long tenantId, boolean platform)
{
}
