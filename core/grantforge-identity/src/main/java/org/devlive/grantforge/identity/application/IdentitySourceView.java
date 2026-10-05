// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.identity.domain.IdentitySourceType;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An identity source as the console shows it, without its secret.
 *
 * @param id the ID
 * @param code the code
 * @param name the name
 * @param type the type
 * @param enabled whether accounts may sign in with it
 * @param provisioning whether unknown users get an account
 * @param ldap the directory settings, for a directory
 * @param oidc the provider settings, for a provider
 * @param secretSet whether a bind password or client secret is stored
 * @param syncIntervalMinutes minutes between automatic syncs, or {@code null}
 * @param lastSyncedAt when it was last synced, or {@code null}
 * @param lastSyncSummary what the last sync did, or {@code null}
 * @param accounts how many accounts sign in with it
 */
public record IdentitySourceView(
        long id,
        String code,
        String name,
        IdentitySourceType type,
        boolean enabled,
        boolean provisioning,
        @Nullable LdapSettings ldap,
        @Nullable OidcSettings oidc,
        boolean secretSet,
        @Nullable Integer syncIntervalMinutes,
        @Nullable Instant lastSyncedAt,
        @Nullable String lastSyncSummary,
        long accounts)
{
}
