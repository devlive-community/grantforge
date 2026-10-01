// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.ResourceView;
import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.jspecify.annotations.Nullable;

/**
 * A resource of the catalog; IDs are strings because they exceed JavaScript's safe integers.
 *
 * @param id the resource ID
 * @param applicationId the application
 * @param parentId the parent, or {@code null} at the top level
 * @param type what it stands for
 * @param code the code, unique in the application
 * @param name the display name
 * @param description the explanation, or {@code null}
 * @param route the console path of a menu, page or tab, or {@code null}
 * @param sortOrder the position among its siblings
 * @param depth the level; top-level resources are at 0
 * @param visible whether a menu or page appears in the navigation
 * @param enabled whether the resource is in use
 * @param denyMode how users without the permission see it
 * @param builtin whether GrantForge declares it (it then keeps its code and cannot be deleted)
 */
public record ResourceResponse(String id, String applicationId, @Nullable String parentId, ResourceType type, String code,
        String name, @Nullable String description, @Nullable String route, int sortOrder, int depth, boolean visible,
        boolean enabled, DenyMode denyMode, boolean builtin)
{
    /**
     * Converts a view.
     *
     * @param resource the view
     * @return the response
     */
    public static ResourceResponse from(ResourceView resource)
    {
        Long parent = resource.parentId();
        ResourceDetails details = resource.details();
        return new ResourceResponse(Long.toString(resource.id()), Long.toString(resource.applicationId()),
                parent == null ? null : Long.toString(parent), resource.type(), resource.code(), details.name(),
                details.description(), details.route(), resource.sortOrder(), resource.depth(), details.visible(),
                details.enabled(), details.denyMode(), resource.builtin());
    }
}
