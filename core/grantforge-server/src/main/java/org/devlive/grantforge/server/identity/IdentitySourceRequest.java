// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.identity.application.IdentitySourceCommand;
import org.devlive.grantforge.identity.domain.IdentitySourceType;
import org.jspecify.annotations.Nullable;

/**
 * An identity source to add or change; the code and type are only taken when adding.
 *
 * @param code the code, unique on the platform
 * @param name what the console and the sign-in page show
 * @param type the type
 * @param enabled whether accounts may sign in with it; on unless said otherwise
 * @param provisioning whether unknown users get an account; on unless said otherwise
 * @param ldap the directory settings, for a directory
 * @param oidc the provider settings, for a provider
 * @param secret the bind password or client secret; left out keeps the stored one, blank removes it
 * @param syncIntervalMinutes minutes between automatic syncs of a directory, or none
 */
public record IdentitySourceRequest(
        @Size(max = 64) @Nullable String code,
        @NotBlank @Size(max = 128) @Nullable String name,
        @NotNull @Nullable IdentitySourceType type,
        @Nullable Boolean enabled,
        @Nullable Boolean provisioning,
        @Valid @Nullable LdapSettingsRequest ldap,
        @Valid @Nullable OidcSettingsRequest oidc,
        @Size(max = 512) @Nullable String secret,
        @Nullable Integer syncIntervalMinutes)
{
    /**
     * Turns the request into a command.
     *
     * @return the command
     * @throws org.devlive.grantforge.common.error.GrantForgeException if the settings cannot work
     */
    public IdentitySourceCommand command()
    {
        IdentitySourceType kind = type == null ? IdentitySourceType.LDAP : type;
        LdapSettingsRequest directory = kind == IdentitySourceType.LDAP ? ldap : null;
        OidcSettingsRequest provider = kind == IdentitySourceType.OIDC ? oidc : null;
        return new IdentitySourceCommand(code == null ? "" : code.strip(), String.valueOf(name), kind, enabled == null || enabled,
                provisioning == null || provisioning, directory == null ? null : directory.settings(), provider == null ? null : provider.settings(),
                secret, syncIntervalMinutes);
    }

    /** Hides the secret from logs. */
    @Override
    public String toString()
    {
        return "IdentitySourceRequest[" + code + ", " + type + "]";
    }
}
