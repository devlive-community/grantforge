// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Something a user may do with a resource, such as {@code read} or {@code select}. Granting an access type also
 * grants the ones it implies, transitively (Hive's {@code all} implies {@code select}, {@code update} and so on).
 *
 * @param name the name, unique within the service type
 * @param label what the console shows
 * @param impliedGrants names of other access types of the same service type that this one grants too
 */
public record AccessTypeDefinition(String name, String label, Set<String> impliedGrants)
{
    /**
     * Checks and copies the values.
     *
     * @throws IllegalArgumentException if a name is malformed, the label blank or the type implies itself
     */
    public AccessTypeDefinition
    {
        Names.name(name, "access type name");
        label = Names.label(label, "label of access type " + name);
        impliedGrants = Set.copyOf(requireNonNull(impliedGrants, "impliedGrants"));
        for (String implied : impliedGrants) {
            Names.name(implied, "access type implied by " + name);
        }
        if (impliedGrants.contains(name)) {
            throw new IllegalArgumentException("access type " + name + " implies itself");
        }
    }

    /**
     * Creates an access type.
     *
     * @param name the name
     * @param label what the console shows
     * @param impliedGrants names of the access types it implies
     * @return the access type
     */
    public static AccessTypeDefinition of(String name, String label, String... impliedGrants)
    {
        return new AccessTypeDefinition(name, label, Set.of(impliedGrants));
    }
}
