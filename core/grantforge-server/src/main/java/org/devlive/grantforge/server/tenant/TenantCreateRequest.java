// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.identity.application.TenantCommand;
import org.jspecify.annotations.Nullable;

/**
 * A new tenant with its first administrator. Formats and the password policy are checked by the service.
 *
 * @param code the stable code: lowercase letters, digits and hyphens, starting with a letter
 * @param name the display name
 * @param adminUsername the administrator's login name, unique across tenants
 * @param adminDisplayName the administrator's display name, if any
 * @param adminPassword the initial password, which the administrator must change at the first sign-in
 */
public record TenantCreateRequest(
        @NotBlank @Size(max = 64) @Nullable String code,
        @NotBlank @Size(max = 128) @Nullable String name,
        @NotBlank @Size(max = 64) @Nullable String adminUsername,
        @Size(max = 128) @Nullable String adminDisplayName,
        @NotBlank @Size(max = 1024) @Nullable String adminPassword)
{
    /**
     * Converts the request.
     *
     * @return the command
     */
    public TenantCommand toCommand()
    {
        return new TenantCommand(code, name, adminUsername, adminDisplayName, adminPassword);
    }

    /**
     * Hides the password from logs and debuggers.
     *
     * @return a description without the password
     */
    @Override
    public String toString()
    {
        return "TenantCreateRequest[code=" + code + ", adminUsername=" + adminUsername + "]";
    }
}
