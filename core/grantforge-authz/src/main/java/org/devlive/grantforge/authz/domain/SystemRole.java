// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The roles GrantForge creates in tenants. */
public enum SystemRole
{
    /** Administers one tenant: its accounts, organization and roles. Exists in every tenant. */
    TENANT_ADMIN("tenant-admin", "Tenant administrator", false, "system"),

    /** Administers the platform: tenants and the resource catalog. Exists only in the platform tenant. */
    PLATFORM_ADMIN("platform-admin", "Platform administrator", true, "system", "platform");

    private final String code;
    private final String defaultName;
    private final boolean platformOnly;
    private final List<String> modules;

    SystemRole(String code, String defaultName, boolean platformOnly, String... modules)
    {
        this.code = code;
        this.defaultName = defaultName;
        this.platformOnly = platformOnly;
        this.modules = List.of(modules);
    }

    /**
     * Returns the console modules whose resources the role allows as a whole; system roles have no grants of their
     * own.
     *
     * @return codes of top-level modules of the console application
     */
    public List<String> modules()
    {
        return modules;
    }

    /**
     * Finds a system role by code.
     *
     * @param code a role code
     * @return the system role, if the code is one
     */
    public static Optional<SystemRole> byCode(String code)
    {
        return Arrays.stream(values()).filter(role -> role.code.equals(code)).findFirst();
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
