// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

/** The roles GrantForge creates in tenants. */
public enum SystemRole
{
    /** Administers one tenant: its accounts, organization and roles. Exists in every tenant. */
    TENANT_ADMIN("tenant-admin", "Tenant administrator", false),

    /** Administers the platform: tenants and the resource catalog. Exists only in the platform tenant. */
    PLATFORM_ADMIN("platform-admin", "Platform administrator", true);

    private final String code;
    private final String defaultName;
    private final boolean platformOnly;

    SystemRole(String code, String defaultName, boolean platformOnly)
    {
        this.code = code;
        this.defaultName = defaultName;
        this.platformOnly = platformOnly;
    }

    /**
     * Returns the role code.
     *
     * @return the code, the same in every tenant
     */
    public String code()
    {
        return code;
    }

    /**
     * Returns the name the role is created with; the console shows a translation by code.
     *
     * @return the English name
     */
    public String defaultName()
    {
        return defaultName;
    }

    /**
     * Returns whether a tenant has the role.
     *
     * @param platform whether the tenant is the platform tenant
     * @return {@code true} if the role exists in such a tenant
     */
    public boolean belongsTo(boolean platform)
    {
        return platform || !platformOnly;
    }
}
