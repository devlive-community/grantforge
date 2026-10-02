// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * The entities of a persistence unit that declare themselves {@link SecuredEntity}, checked against the mapping: codes
 * are well-formed and unique, filterable fields are basic attributes of supported types, and owner, department and
 * tenant attributes are {@code Long} attributes. A declaration that does not fit stops the start.
 */
public final class SecuredEntities
{
    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9-]{1,40}");
    private static final Set<Class<?>> NUMBERS = Set.of(Integer.class, int.class, Long.class, long.class, Short.class, short.class,
            BigDecimal.class, BigInteger.class, Double.class, double.class, Float.class, float.class);
    private static final Set<Class<?>> TIMES = Set.of(Instant.class, LocalDate.class, LocalDateTime.class, OffsetDateTime.class);
    private static final Set<Class<?>> IDS = Set.of(Long.class, long.class);

    private final List<SecuredEntityDefinition> definitions;

    private SecuredEntities(List<SecuredEntityDefinition> definitions)
    {
        this.definitions = List.copyOf(definitions);
    }

    /**
     * Returns a registry without entities, for applications without JPA.
     *
     * @return the empty registry
     */
    public static SecuredEntities none()
    {
        return new SecuredEntities(List.of());
    }

    /**
     * Reads and checks the declarations of a persistence unit.
     *
     * @param entityManagerFactory the persistence unit
     * @return the entities, by code
     * @throws IllegalStateException listing every declaration that does not fit the mapping
     */
    public static SecuredEntities of(EntityManagerFactory entityManagerFactory)
    {
        List<String> problems = new ArrayList<>();
        List<SecuredEntityDefinition> found = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (EntityType<?> entity : requireNonNull(entityManagerFactory, "entityManagerFactory").getMetamodel().getEntities()) {
            SecuredEntity declared = entity.getJavaType().getAnnotation(SecuredEntity.class);
            if (declared == null) {
                continue;
            }
            String where = entity.getJavaType().getSimpleName();
            if (!CODE.matcher(declared.code()).matches()) {
                problems.add(where + ": code '" + declared.code() + "' must be 2 to 41 lowercase letters, digits or '-'");
            }
            else if (!codes.add(declared.code())) {
                problems.add(where + ": code '" + declared.code() + "' is used twice");
            }
            if (declared.name().isBlank()) {
                problems.add(where + ": the name is blank");
            }
            String owner = id(entity, declared.owner(), "owner", problems);
            String unit = id(entity, declared.unit(), "unit", problems);
            String tenant = id(entity, declared.tenant(), "tenant", problems);
            if (declared.unitFromOwner() && (declared.owner().isBlank() || !declared.unit().isBlank())) {
                problems.add(where + ": unitFromOwner needs an owner and no unit");
            }
            boolean tenantScoped = TenantScopedEntity.class.isAssignableFrom(entity.getJavaType());
            if (tenantScoped && tenant != null) {
                problems.add(where + ": a tenant-scoped entity declares no tenant attribute");
            }
            found.add(new SecuredEntityDefinition(declared.code(), declared.name().strip(), entity.getJavaType(),
                    fields(entity, problems), owner, unit, declared.unitFromOwner(), tenantScoped, tenant));
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("secured entities do not fit their mapping:\n  " + String.join("\n  ", problems));
        }
        found.sort(Comparator.comparing(SecuredEntityDefinition::code));
        return new SecuredEntities(found);
    }

    /**
     * Returns every secured entity.
     *
     * @return the entities, by code
     */
    public List<SecuredEntityDefinition> all()
    {
        return definitions;
    }

    /**
     * Finds a secured entity.
     *
     * @param code its code
     * @return the entity, or empty
     */
    public Optional<SecuredEntityDefinition> find(String code)
    {
        return definitions.stream().filter(definition -> definition.code().equals(code)).findFirst();
    }

    /**
     * Finds the secured entity of a class.
     *
     * @param type the entity class
     * @return the entity, or empty if the class is not secured
     */
    public Optional<SecuredEntityDefinition> find(Class<?> type)
    {
        return definitions.stream().filter(definition -> definition.type().equals(type)).findFirst();
    }

    /** Checks an id attribute; returns its name, or {@code null} if none is declared. */
    private static @Nullable String id(EntityType<?> entity, String name, String role, List<String> problems)
    {
        if (name.isBlank()) {
            return null;
        }
        Optional<SingularAttribute<?, ?>> attribute = singular(entity, name);
        if (attribute.isEmpty()) {
            problems.add(entity.getJavaType().getSimpleName() + ": " + role + " attribute '" + name + "' is not mapped");
            return null;
        }
        if (!IDS.contains(attribute.orElseThrow().getJavaType())) {
            problems.add(entity.getJavaType().getSimpleName() + ": " + role + " attribute '" + name + "' is no Long");
        }
        return name;
    }

    private static List<DataField> fields(EntityType<?> entity, List<String> problems)
    {
        List<DataField> fields = new ArrayList<>();
        for (Field field : declaredFields(entity.getJavaType())) {
            FilterableField marked = field.getAnnotation(FilterableField.class);
            if (marked == null) {
                continue;
            }
            String where = entity.getJavaType().getSimpleName() + "." + field.getName();
            Optional<SingularAttribute<?, ?>> attribute = singular(entity, field.getName());
            if (attribute.isEmpty() || attribute.orElseThrow().getPersistentAttributeType() != Attribute.PersistentAttributeType.BASIC) {
                problems.add(where + ": a filterable field must be a basic mapped attribute");
                continue;
            }
            Class<?> type = attribute.orElseThrow().getJavaType();
            DataFieldType kind = kind(type);
            if (kind == null) {
                problems.add(where + ": " + type.getSimpleName() + " fields cannot be filtered");
                continue;
            }
            List<String> choices = kind == DataFieldType.CHOICE
                    ? Arrays.stream(type.getEnumConstants()).map(value -> ((Enum<?>) value).name()).toList() : List.of();
            fields.add(new DataField(field.getName(), marked.value().strip(), kind, choices));
        }
        return fields;
    }

    /** The fields of a class and its superclasses, superclass fields first. */
    private static List<Field> declaredFields(Class<?> type)
    {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            fields.addAll(0, Arrays.asList(current.getDeclaredFields()));
        }
        return fields;
    }

    private static Optional<SingularAttribute<?, ?>> singular(EntityType<?> entity, String name)
    {
        return entity.getSingularAttributes().stream().filter(attribute -> attribute.getName().equals(name))
                .<SingularAttribute<?, ?>>map(attribute -> attribute).findFirst();
    }

    private static @Nullable DataFieldType kind(Class<?> type)
    {
        if (type == String.class) {
            return DataFieldType.TEXT;
        }
        if (type.isEnum()) {
            return DataFieldType.CHOICE;
        }
        if (type == Boolean.class || type == boolean.class) {
            return DataFieldType.BOOLEAN;
        }
        if (NUMBERS.contains(type)) {
            return DataFieldType.NUMBER;
        }
        return TIMES.contains(type) ? DataFieldType.TIME : null;
    }
}
