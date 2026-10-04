// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.open;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.authz.data.EntityDeclaration;
import org.devlive.grantforge.persistence.secured.DataField;
import org.devlive.grantforge.persistence.secured.DataFieldType;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNullElse;

/**
 * The data entities an application declares: all of them, replacing what it declared before.
 *
 * @param entities the entities
 */
public record OpenEntitiesRequest(@NotNull @Size(max = 100) @Nullable List<@NotNull @Valid OpenEntity> entities)
{
    /** Copies the entities; left out, validation refuses them. */
    // Absent stays absent: validation refuses it or the default applies later.
    @SuppressWarnings("PMD.NullAssignment")
    public OpenEntitiesRequest
    {
        entities = entities == null ? null : List.copyOf(entities);
    }

    /**
     * Converts the request.
     *
     * @return the declarations
     */
    List<EntityDeclaration> declarations()
    {
        return requireNonNullElse(entities, List.<OpenEntity>of()).stream().map(OpenEntity::declaration).toList();
    }

    /**
     * An entity of the application.
     *
     * @param code its code in the application, such as {@code order}
     * @param name what its rows are
     * @param owned whether rows belong to an account; no when absent
     * @param unitBased whether rows belong to a department; no when absent
     * @param fields the fields conditions may test
     */
    public record OpenEntity(@NotBlank @Nullable String code, @NotBlank @Nullable String name, @Nullable Boolean owned, @Nullable Boolean unitBased,
            @Size(max = 50) @Nullable List<@NotNull @Valid OpenEntityField> fields)
    {
        /** Copies the fields. */
        // Absent stays absent: validation refuses it or the default applies later.
        @SuppressWarnings("PMD.NullAssignment")
        public OpenEntity
        {
            fields = fields == null ? null : List.copyOf(fields);
        }

        EntityDeclaration declaration()
        {
            return new EntityDeclaration(requireNonNullElse(code, ""), requireNonNullElse(name, ""), Boolean.TRUE.equals(owned),
                    Boolean.TRUE.equals(unitBased),
                    requireNonNullElse(fields, List.<OpenEntityField>of()).stream().map(OpenEntityField::field).toList());
        }
    }

    /**
     * A field conditions may test.
     *
     * @param code the field's code, as the application's queries name it
     * @param name how people call it
     * @param type its kind of values
     * @param choices the values of a choice field
     */
    public record OpenEntityField(@NotBlank @Nullable String code, @NotBlank @Nullable String name, @NotNull @Nullable DataFieldType type,
            @Size(max = 50) @Nullable List<String> choices)
    {
        /** Copies the choices. */
        // Absent stays absent: validation refuses it or the default applies later.
        @SuppressWarnings("PMD.NullAssignment")
        public OpenEntityField
        {
            choices = choices == null ? null : List.copyOf(choices);
        }

        DataField field()
        {
            return new DataField(requireNonNullElse(code, ""), requireNonNullElse(name, ""), requireNonNullElse(type, DataFieldType.TEXT),
                    requireNonNullElse(choices, List.of()));
        }
    }
}
