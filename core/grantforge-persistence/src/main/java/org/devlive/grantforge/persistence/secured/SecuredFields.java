// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Finds the {@link SecuredField}s a type carries: in its record components, and in those of the records they hold, such as
 * the items of a list or the rows of a page. Only records are looked into; of other types, such as lists or response
 * entities, only the type arguments.
 */
public final class SecuredFields
{
    private static final Pattern ENTITY = Pattern.compile("[a-z][a-z0-9-]{1,40}");
    private static final Pattern FIELD = Pattern.compile("[a-z][a-zA-Z0-9]{0,63}");

    private SecuredFields()
    {
    }

    /**
     * Returns the secured fields a type carries.
     *
     * @param type the type, such as the generic return type of an API method
     * @return the fields, in the order they are met
     * @throws IllegalStateException if a declaration has an invalid entity or field code, or if two declarations of the same
     *         field give different names
     */
    public static Set<DeclaredField> in(Type type)
    {
        Set<DeclaredField> found = new LinkedHashSet<>();
        visit(requireNonNull(type, "type"), Map.of(), new HashSet<>(), found);
        Map<String, String> names = new HashMap<>();
        for (DeclaredField field : found) {
            check(field);
            String other = names.putIfAbsent(field.resourceCode(), field.name());
            if (other != null && !other.equals(field.name())) {
                throw new IllegalStateException("secured field " + field.resourceCode() + " is named both " + other + " and "
                        + field.name());
            }
        }
        return found;
    }

    private static void check(DeclaredField field)
    {
        if (!ENTITY.matcher(field.entity()).matches() || !FIELD.matcher(field.field()).matches() || field.name().isBlank()) {
            throw new IllegalStateException("invalid secured field " + field.entity() + "." + field.field() + " ("
                    + field.name() + ")");
        }
    }

    private static void visit(Type type, Map<TypeVariable<?>, Type> bindings, Set<String> seen, Set<DeclaredField> found)
    {
        if (type instanceof ParameterizedType parameterized) {
            Class<?> raw = (Class<?>) parameterized.getRawType();
            Map<TypeVariable<?>, Type> inner = new HashMap<>(bindings);
            TypeVariable<?>[] variables = raw.getTypeParameters();
            Type[] arguments = parameterized.getActualTypeArguments();
            for (int i = 0; i < variables.length; i++) {
                inner.put(variables[i], resolve(arguments[i], bindings));
            }
            for (Type argument : arguments) {
                visit(resolve(argument, bindings), bindings, seen, found);
            }
            members(raw, inner, seen, found);
        }
        else if (type instanceof Class<?> plain) {
            if (plain.isArray()) {
                visit(plain.getComponentType(), bindings, seen, found);
            }
            else {
                members(plain, bindings, seen, found);
            }
        }
        else if (type instanceof GenericArrayType array) {
            visit(array.getGenericComponentType(), bindings, seen, found);
        }
        else if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                visit(bound, bindings, seen, found);
            }
        }
        else if (type instanceof TypeVariable<?> variable) {
            Type bound = bindings.get(variable);
            if (bound != null) {
                visit(bound, bindings, seen, found);
            }
        }
    }

    private static Type resolve(Type type, Map<TypeVariable<?>, Type> bindings)
    {
        Type bound = type instanceof TypeVariable<?> variable ? bindings.get(variable) : null;
        return bound == null ? type : bound;
    }

    private static void members(Class<?> type, Map<TypeVariable<?>, Type> bindings, Set<String> seen, Set<DeclaredField> found)
    {
        if (!type.isRecord() || !seen.add(key(type, bindings))) {
            return;
        }
        for (RecordComponent component : type.getRecordComponents()) {
            SecuredField declared = component.getAnnotation(SecuredField.class);
            if (declared != null) {
                found.add(DeclaredField.of(declared));
            }
            visit(component.getGenericType(), bindings, seen, found);
        }
    }

    /** A generic type such as a page is looked into once per element type. */
    private static String key(Class<?> type, Map<TypeVariable<?>, Type> bindings)
    {
        StringBuilder key = new StringBuilder(type.getName());
        for (TypeVariable<?> variable : type.getTypeParameters()) {
            key.append(' ').append(bindings.getOrDefault(variable, variable).getTypeName());
        }
        return key.toString();
    }
}
