// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import static java.util.Objects.requireNonNull;

/**
 * A field put under field permissions by {@link SecuredField}.
 *
 * @param entity the entity's code
 * @param field the field's code within the entity
 * @param name what the field is
 */
public record DeclaredField(String entity, String field, String name)
{
    /** Checks that every value is present. */
    public DeclaredField
    {
        requireNonNull(entity, "entity");
        requireNonNull(field, "field");
        requireNonNull(name, "name");
    }

    /**
     * Creates the field an annotation declares.
     *
     * @param annotation the annotation
     * @return the field
     */
    public static DeclaredField of(SecuredField annotation)
    {
        return new DeclaredField(annotation.entity(), annotation.field(), annotation.name());
    }

    /**
     * Returns the code of the field's catalog resource.
     *
     * @return {@code entity:} followed by the entity's code, a dot and the field's code
     */
    public String resourceCode()
    {
        return SecuredEntityDefinition.RESOURCE_PREFIX + entity + "." + field;
    }
}
