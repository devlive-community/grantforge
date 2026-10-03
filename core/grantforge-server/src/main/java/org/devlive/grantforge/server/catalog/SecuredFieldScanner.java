// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.FieldAppearance;
import org.devlive.grantforge.authz.domain.FieldDirection;
import org.devlive.grantforge.persistence.secured.DeclaredField;
import org.devlive.grantforge.persistence.secured.SecuredEntities;
import org.devlive.grantforge.persistence.secured.SecuredFields;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Lists where the API endpoints return or accept {@link org.devlive.grantforge.persistence.secured.SecuredField}s: in what a
 * handler returns and in its request body.
 */
@Component
@ConditionalOnWebApplication
public final class SecuredFieldScanner
{
    private final RequestMappingHandlerMapping mappings;
    private final SecuredEntities entities;

    /**
     * Creates the scanner.
     *
     * @param mappings Spring MVC's annotated handler mappings
     * @param entities the secured entities, which every field must belong to
     */
    public SecuredFieldScanner(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings,
            SecuredEntities entities)
    {
        this.mappings = requireNonNull(mappings, "mappings");
        this.entities = requireNonNull(entities, "entities");
    }

    /**
     * Returns every appearance of a secured field in an API endpoint, by path, method and field.
     *
     * @return the appearances
     * @throws IllegalStateException listing every declaration that names no secured entity or does not fit, and every
     *         field named differently in different places
     */
    public List<FieldAppearance> scan()
    {
        List<FieldAppearance> found = new ArrayList<>();
        Set<String> problems = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.getHandlerMethods().entrySet()) {
            try {
                found.addAll(appearances(entry.getKey(), entry.getValue()));
            }
            catch (IllegalStateException problem) {
                problems.add(entry.getValue().getShortLogMessage() + ": " + problem.getMessage());
            }
        }
        found.stream().map(FieldAppearance::field).filter(field -> entities.find(field.entity()).isEmpty())
                .forEach(field -> problems.add("secured field " + field.resourceCode() + " names no secured entity"));
        found.stream().map(FieldAppearance::field).collect(Collectors.groupingBy(DeclaredField::resourceCode,
                Collectors.mapping(DeclaredField::name, Collectors.toCollection(TreeSet::new)))).forEach((code, names) -> {
                    if (names.size() > 1) {
                        problems.add("secured field " + code + " is named " + String.join(" and ", names));
                    }
                });
        if (!problems.isEmpty()) {
            throw new IllegalStateException("secured fields do not fit: " + String.join("; ", problems));
        }
        found.sort(Comparator.comparing(FieldAppearance::pathPattern).thenComparing(FieldAppearance::httpMethod)
                .thenComparing(appearance -> appearance.field().resourceCode()).thenComparing(FieldAppearance::direction));
        return found;
    }

    private static List<FieldAppearance> appearances(RequestMappingInfo mapping, HandlerMethod handler)
    {
        Set<DeclaredField> returned = SecuredFields.in(handler.getReturnType().getGenericParameterType());
        Set<DeclaredField> accepted = Arrays.stream(handler.getMethodParameters())
                .filter(parameter -> parameter.hasParameterAnnotation(RequestBody.class))
                .flatMap(parameter -> SecuredFields.in(parameter.getGenericParameterType()).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return mapping.getPatternValues().stream().filter(path -> path.startsWith(ApiEndpointScanner.API_PREFIX))
                .flatMap(path -> mapping.getMethodsCondition().getMethods().stream().flatMap(method -> Stream.concat(
                        returned.stream().map(field -> new FieldAppearance(field, method.name(), path, FieldDirection.READ)),
                        accepted.stream().map(field -> new FieldAppearance(field, method.name(), path, FieldDirection.WRITE)))))
                .toList();
    }
}
