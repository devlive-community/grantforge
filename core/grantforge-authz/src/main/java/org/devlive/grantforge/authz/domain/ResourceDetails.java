// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * The changeable settings of a resource.
 *
 * @param name display name
 * @param description optional explanation
 * @param route the console path of a menu, page or tab, such as {@code /admin/users}; {@code null} for others
 * @param visible whether a menu or page appears in the navigation
 * @param enabled whether the resource is in use; disabled resources grant nothing
 * @param denyMode how the console shows the resource to users who may not use it
 */
public record ResourceDetails(String name, @Nullable String description, @Nullable String route, boolean visible,
        boolean enabled, DenyMode denyMode)
{
    /** Checks that the required values are present. */
    public ResourceDetails
    {
        requireNonNull(name, "name");
        requireNonNull(denyMode, "denyMode");
    }
}
