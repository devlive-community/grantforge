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
 * @param administratorId the designated account, if any
 */
public record ApplicationResponse(String id, String code, String name, @Nullable String description, boolean builtin,
        long resources, @Nullable String administratorId)
{
    /**
     * Creates a response without a designated administrator.
     * @param id application ID
     * @param code application code
     * @param name application name
     * @param description application description
     * @param builtin whether it is built in
     * @param resources resource count
     */
    public ApplicationResponse(String id, String code, String name, @Nullable String description, boolean builtin, long resources)
    {
        this(id, code, name, description, builtin, resources, null);
    }
    /**
     * Converts a view.
     *
     * @param application the view
     * @return the response
     */
    public static ApplicationResponse from(ApplicationView application)
    {
        Long administratorId = application.administratorId();
        return new ApplicationResponse(Long.toString(application.id()), application.code(), application.name(),
                application.description(), application.builtin(), application.resources(),
                administratorId == null ? null : Long.toString(administratorId));
    }
}
