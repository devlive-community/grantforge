// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.jspecify.annotations.Nullable;

/**
 * Input for creating a tenant with its first administrator. Values are validated by the service.
 *
 * @param code the stable code
 * @param name the display name
 * @param adminUsername the administrator's login name, unique across tenants
 * @param adminDisplayName the administrator's display name, if any
 * @param adminPassword the administrator's initial password, which must be changed at the first sign-in
 */
public record TenantCommand(@Nullable String code, @Nullable String name, @Nullable String adminUsername,
        @Nullable String adminDisplayName, @Nullable String adminPassword)
{
    /**
     * Hides the password from logs and debuggers.
     *
     * @return a description without the password
     */
    @Override
    public String toString()
    {
        return "TenantCommand[code=" + code + ", adminUsername=" + adminUsername + "]";
    }
}
