// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.identity;

import org.devlive.grantforge.identity.application.IdentitySourceView;
import org.devlive.grantforge.identity.application.LdapSettings;
import org.devlive.grantforge.identity.application.OidcSettings;
import org.devlive.grantforge.identity.domain.IdentitySourceType;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An identity source, without its secret.
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
 * @param syncIntervalMinutes minutes between automatic syncs, or none
 * @param lastSyncedAt when it was last synced
 * @param lastSyncSummary what the last sync did
 * @param accounts how many accounts sign in with it
 * @param callbackPath where a provider sends users back, for its client registration
 */
public record IdentitySourceResponse(
        String id,
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
        long accounts,
        @Nullable String callbackPath)
{
    /** Where providers send users back, followed by the source's code. */
    public static final String CALLBACK = "/api/v1/auth/federated/callback/";

    /**
     * Converts a view.
     *
     * @param view the source
     * @return the response
     */
    public static IdentitySourceResponse from(IdentitySourceView view)
    {
        return new IdentitySourceResponse(Long.toString(view.id()), view.code(), view.name(), view.type(), view.enabled(), view.provisioning(),
                view.ldap(), view.oidc(), view.secretSet(), view.syncIntervalMinutes(), view.lastSyncedAt(), view.lastSyncSummary(),
                view.accounts(), view.type() == IdentitySourceType.OIDC ? CALLBACK + view.code() : null);
    }
}
