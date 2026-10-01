// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.Application;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * An application of the catalog.
 *
 * @param id the application ID
 * @param code the code
 * @param name the display name
 * @param description the explanation, or {@code null}
 * @param builtin whether it is part of GrantForge
 * @param resources how many resources it has
 */
public record ApplicationView(long id, String code, String name, @Nullable String description, boolean builtin,
        long resources)
{
    /** Validates the values. */
    public ApplicationView
    {
        requireNonNull(code, "code");
        requireNonNull(name, "name");
    }

    /**
     * Converts an application.
     *
     * @param application the application
     * @param resources how many resources it has
     * @return the view
     */
    public static ApplicationView from(Application application, long resources)
    {
        return new ApplicationView(application.requireId(), application.getCode(), application.getName(),
                application.getDescription(), application.isBuiltin(), resources);
    }
}
