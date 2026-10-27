// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.application;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Public configuration and a separately supplied credential.
 *
 * @param code                immutable tenant code
 * @param name                display name
 * @param type                provider type
 * @param identitySourceId    the OIDC source whose accounts may be linked
 * @param enabled             whether jobs may run
 * @param loginUpdates        whether sign-ins reconcile memberships
 * @param settings            public provider settings
 * @param parentUnitId        optional local mount
 * @param syncIntervalMinutes automatic schedule, or manual only
 * @param secret              write-only credential; blank preserves the stored value
 */
public record DataSourceCommand(String code, String name, String type, long identitySourceId, boolean enabled,
                                boolean loginUpdates,
                                Map<String, String> settings, @Nullable Long parentUnitId,
                                @Nullable Integer syncIntervalMinutes, @Nullable String secret)
{
    /** Copies the public provider settings. */
    public DataSourceCommand
    {
        settings = Map.copyOf(settings);
    }

    /**
     * Hides write-only credentials from logs.
     */
    @Override
    public String toString()
    {
        return "DataSourceCommand[" + code + ", " + type + "]";
    }
}
