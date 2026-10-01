// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A resource of the catalog.
 *
 * @param id the resource ID
 * @param applicationId the application
 * @param parentId the parent, or {@code null} at the top level
 * @param type what it stands for
 * @param code the code, unique in the application
 * @param details the changeable settings
 * @param sortOrder the position among its siblings
 * @param depth the level; top-level resources are at 0
 * @param builtin whether GrantForge declares it
 */
public record ResourceView(long id, long applicationId, @Nullable Long parentId, ResourceType type, String code,
        ResourceDetails details, int sortOrder, int depth, boolean builtin)
{
    /** Validates the values. */
    public ResourceView
    {
        requireNonNull(type, "type");
        requireNonNull(code, "code");
        requireNonNull(details, "details");
    }

    /**
     * Converts a resource.
     *
     * @param resource the resource
     * @return the view
     */
    public static ResourceView from(Resource resource)
    {
        return new ResourceView(resource.requireId(), resource.getApplicationId(), resource.getParentId(),
                resource.getType(), resource.getCode(), resource.getDetails(), resource.getSortOrder(),
                resource.getDepth(), resource.isBuiltin());
    }
}
