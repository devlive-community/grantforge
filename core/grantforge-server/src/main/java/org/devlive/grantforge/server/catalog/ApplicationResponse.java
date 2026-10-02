// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.ApplicationView;
import org.jspecify.annotations.Nullable;

/**
 * An application of the catalog; IDs are strings because they exceed JavaScript's safe integers.
 *
 * @param id the application ID
 * @param code the code
 * @param name the display name
 * @param description the explanation, or {@code null}
 * @param builtin whether it is part of GrantForge (and cannot be deleted)
 * @param resources how many resources it has
 */
public record ApplicationResponse(String id, String code, String name, @Nullable String description, boolean builtin,
        long resources)
{
    /**
     * Converts a view.
     *
     * @param application the view
     * @return the response
     */
    public static ApplicationResponse from(ApplicationView application)
    {
        return new ApplicationResponse(Long.toString(application.id()), application.code(), application.name(),
                application.description(), application.builtin(), application.resources());
    }
}
