// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNullElse;

/** Turns request fields into resource settings. */
final class ResourceSettings
{
    private ResourceSettings()
    {
    }

    static ResourceDetails of(@Nullable String name, @Nullable String description, @Nullable String route,
            @Nullable Boolean visible, @Nullable Boolean enabled, @Nullable DenyMode denyMode)
    {
        return new ResourceDetails(String.valueOf(name), description, route, !Boolean.FALSE.equals(visible),
                !Boolean.FALSE.equals(enabled), requireNonNullElse(denyMode, DenyMode.HIDE));
    }
}
