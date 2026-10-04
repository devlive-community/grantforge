// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.identity.domain.IdentitySourceType;
import org.jspecify.annotations.Nullable;

/**
 * What an administrator enters for an identity source.
 *
 * @param code the code, unique on the platform; fixed once created
 * @param name what the console and the sign-in page show
 * @param type the type; fixed once created
 * @param enabled whether accounts may sign in with it
 * @param provisioning whether unknown users who sign in, or a sync finds, get an account
 * @param ldap the directory settings, for {@link IdentitySourceType#LDAP}
 * @param oidc the provider settings, for {@link IdentitySourceType#OIDC}
 * @param secret the bind password or client secret; {@code null} keeps the stored one, blank removes it
 * @param syncIntervalMinutes minutes between automatic syncs of a directory, 15 to 10080, or {@code null} for none
 */
public record IdentitySourceCommand(
        String code,
        String name,
        IdentitySourceType type,
        boolean enabled,
        boolean provisioning,
        @Nullable LdapSettings ldap,
        @Nullable OidcSettings oidc,
        @Nullable String secret,
        @Nullable Integer syncIntervalMinutes)
{
    /** Hides the secret from logs. */
    @Override
    public String toString()
    {
        return "IdentitySourceCommand[" + code + ", " + type + "]";
    }
}
