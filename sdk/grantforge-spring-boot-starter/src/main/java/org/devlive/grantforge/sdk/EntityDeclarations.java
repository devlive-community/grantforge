// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/** Turns {@link GrantForgeEntity} classes into what the open API takes as their declaration. */
public final class EntityDeclarations
{
    private static final Set<Class<?>> NUMBERS = Set.of(long.class, int.class, short.class, double.class, float.class, Long.class,
            Integer.class, Short.class, Double.class, Float.class, BigDecimal.class, BigInteger.class);
    private static final Set<Class<?>> MOMENTS = Set.of(Instant.class, LocalDate.class, LocalDateTime.class, OffsetDateTime.class,
            ZonedDateTime.class, Date.class);

    private EntityDeclarations()
    {
    }

    /**
     * Declares entities.
     *
     * @param types the entity classes, annotated with {@link GrantForgeEntity}
     * @return the body of {@code PUT /api/v1/open/catalog/data-entities}
     * @throws IllegalArgumentException if a class is not annotated or a field's type cannot be tested
     */
    public static Request of(Collection<Class<?>> types)
    {
        return new Request(types.stream().map(EntityDeclarations::entity).toList());
    }

    private static Entity entity(Class<?> type)
    {
        GrantForgeEntity entity = type.getAnnotation(GrantForgeEntity.class);
        if (entity == null) {
            throw new IllegalArgumentException(type.getName() + " is not annotated with @GrantForgeEntity");
        }
        List<FieldDeclaration> fields = new ArrayList<>();
        for (Class<?> level = type; level != null && level != Object.class; level = level.getSuperclass()) {
            for (Field field : level.getDeclaredFields()) {
                GrantForgeField declared = field.getAnnotation(GrantForgeField.class);
                if (declared != null) {
                    fields.add(field(type, field, declared));
                }
            }
        }
        return new Entity(entity.code(), entity.name(), !entity.owner().isEmpty(), !entity.unit().isEmpty(), fields);
    }

    private static FieldDeclaration field(Class<?> owner, Field field, GrantForgeField declared)
    {
        Class<?> type = field.getType();
        if (type.isEnum()) {
            return new FieldDeclaration(field.getName(), declared.value(), "CHOICE",
                    Arrays.stream(type.getEnumConstants()).map(constant -> ((Enum<?>) constant).name()).toList());
        }
        String kind;
        if (type == String.class) {
            kind = "TEXT";
        }
        else if (NUMBERS.contains(type)) {
            kind = "NUMBER";
        }
        else if (type == boolean.class || type == Boolean.class) {
            kind = "BOOLEAN";
        }
        else if (MOMENTS.contains(type)) {
            kind = "TIME";
        }
        else {
            throw new IllegalArgumentException(owner.getName() + "." + field.getName() + " is a " + type.getName()
                    + ", which conditions cannot test");
        }
        return new FieldDeclaration(field.getName(), declared.value(), kind, List.of());
    }

    /**
     * The declarations of an application's entities.
     *
     * @param entities the entities
     */
    public record Request(List<Entity> entities)
    {
        /** Copies the list. */
        public Request
        {
            entities = List.copyOf(entities);
        }
    }

    /**
     * An entity's declaration.
     *
     * @param code its code
     * @param name what its rows are
     * @param owned whether rows have an owner column
     * @param unitBased whether rows have a department column
     * @param fields the fields conditions may test
     */
    public record Entity(String code, String name, boolean owned, boolean unitBased, List<FieldDeclaration> fields)
    {
        /** Checks and copies the parts. */
        public Entity
        {
            requireNonNull(code, "code");
            requireNonNull(name, "name");
            fields = List.copyOf(fields);
        }
    }

    /**
     * A field's declaration.
     *
     * @param code the attribute
     * @param name how people call it
     * @param type TEXT, NUMBER, BOOLEAN, CHOICE or TIME
     * @param choices the choices of a CHOICE field
     */
    public record FieldDeclaration(String code, String name, String type, List<String> choices)
    {
        /** Checks and copies the parts. */
        public FieldDeclaration
        {
            requireNonNull(code, "code");
            requireNonNull(name, "name");
            requireNonNull(type, "type");
            choices = List.copyOf(choices);
        }
    }
}
