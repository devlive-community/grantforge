// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.jspecify.annotations.Nullable;

/**
 * New details of a resource; its type never changes and moving has its own call. Built-in resources keep their
 * code.
 *
 * @param code the code
 * @param name the display name
 * @param description the explanation; blank removes it
 * @param route the console path of a menu, page or tab; blank removes it
 * @param visible whether a menu or page appears in the navigation; {@code true} if omitted
 * @param enabled whether the resource is in use; {@code true} if omitted
 * @param denyMode how users without the permission see it; {@code HIDE} if omitted
 */
public record ResourceUpdateRequest(
        @NotBlank @Size(max = 255) @Nullable String code,
        @NotBlank @Size(max = 128) @Nullable String name,
        @Size(max = 500) @Nullable String description,
        @Size(max = 255) @Nullable String route,
        @Nullable Boolean visible,
        @Nullable Boolean enabled,
        @Nullable DenyMode denyMode)
{
    /**
     * Returns the settings, with defaults for omitted flags.
     *
     * @return the settings
     */
    ResourceDetails details()
    {
        return ResourceSettings.of(name, description, route, visible, enabled, denyMode);
    }
}
