// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDependency;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * One application's resources and dependencies as permissions are worked out from them, with the resources that
 * are disabled. Changing a copy (another dependency, a disabled resource) shows what such a change would do.
 *
 * @param applicationId the application
 * @param console whether it is the console, whose modules system roles allow as a whole
 * @param tree its resources
 * @param dependencies its dependencies
 * @param disabled the resources that are disabled; they and everything below them grant nothing
 */
record CatalogView(long applicationId, boolean console, List<Resource> tree, List<ResourceDependency> dependencies,
        Set<Long> disabled)
{
    /** Copies the lists. */
    CatalogView
    {
        tree = List.copyOf(requireNonNull(tree, "tree"));
        dependencies = List.copyOf(requireNonNull(dependencies, "dependencies"));
        disabled = Set.copyOf(requireNonNull(disabled, "disabled"));
    }

    /**
     * Returns the view of a loaded catalog, with the resources disabled as stored.
     *
     * @param applicationId the application
     * @param console whether it is the console
     * @param tree its resources
     * @param dependencies its dependencies
     * @return the view
     */
    static CatalogView of(long applicationId, boolean console, List<Resource> tree, List<ResourceDependency> dependencies)
    {
        Set<Long> disabled = new HashSet<>();
        tree.stream().filter(resource -> !resource.isEnabled()).forEach(resource -> disabled.add(resource.requireId()));
        return new CatalogView(applicationId, console, tree, dependencies, disabled);
    }

    /**
     * Returns the resources by ID.
     *
     * @return the resources
     */
    Map<Long, Resource> byId()
    {
        Map<Long, Resource> found = new LinkedHashMap<>();
        tree.forEach(resource -> found.put(resource.requireId(), resource));
        return found;
    }

    /**
     * Returns whether a resource is disabled itself or lies below a disabled resource.
     *
     * @param byId the resources by ID, from {@link #byId()}
     * @param resourceId the resource
     * @return {@code true} if it grants nothing
     */
    boolean switchedOff(Map<Long, Resource> byId, long resourceId)
    {
        if (disabled.contains(resourceId)) {
            return true;
        }
        Resource resource = byId.get(resourceId);
        while (resource != null) {
            Long parent = resource.getParentId();
            if (parent == null) {
                return false;
            }
            if (disabled.contains(parent)) {
                return true;
            }
            resource = byId.get(parent);
        }
        return false;
    }

    /**
     * Returns a copy with other dependencies.
     *
     * @param changed the dependencies
     * @return the copy
     */
    CatalogView withDependencies(List<ResourceDependency> changed)
    {
        return new CatalogView(applicationId, console, tree, changed, disabled);
    }

    /**
     * Returns a copy with a resource enabled or disabled.
     *
     * @param resourceId the resource
     * @param enabled whether it is enabled
     * @return the copy
     */
    CatalogView withEnabled(long resourceId, boolean enabled)
    {
        Set<Long> changed = new HashSet<>(disabled);
        if (enabled) {
            changed.remove(resourceId);
        }
        else {
            changed.add(resourceId);
        }
        return new CatalogView(applicationId, console, tree, dependencies, changed);
    }
}
