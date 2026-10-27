// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.application;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;

/**
 * Credential-free configuration returned to administrators.
 *
 * @param id                  the source ID
 * @param code                the code
 * @param name                the name
 * @param type                the provider
 * @param identitySourceId    the OIDC source
 * @param identitySourceName  display name of the OIDC source
 * @param enabled             whether jobs are enabled
 * @param loginUpdates        whether logins update memberships
 * @param settings            public provider settings
 * @param parentUnitId        optional mount
 * @param syncIntervalMinutes optional schedule
 * @param secretSet           whether a credential is stored
 * @param initialized         whether managed departments exist
 * @param lastSyncedAt        last attempt time
 * @param lastSyncSummary     last safe result
 */
public record DataSourceView(String id, String code, String name, String type, String identitySourceId,
                             String identitySourceName, boolean enabled, boolean loginUpdates,
                             Map<String, String> settings, @Nullable String parentUnitId,
                             @Nullable Integer syncIntervalMinutes, boolean secretSet, boolean initialized,
                             @Nullable Instant lastSyncedAt, @Nullable String lastSyncSummary)
{
}
