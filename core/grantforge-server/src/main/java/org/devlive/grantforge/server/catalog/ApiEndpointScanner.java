// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.DeclaredEndpoint;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/** Lists the API endpoints of the running web server ({@value #API_PREFIX}...) with their access declarations. */
@Component
@ConditionalOnWebApplication
public final class ApiEndpointScanner
{
    /** Paths of the API; others (the console's files, actuator, the OpenAPI document) are not catalogued. */
    static final String API_PREFIX = "/api/";

    private final RequestMappingHandlerMapping mappings;

    /**
     * Creates the scanner.
     *
     * @param mappings Spring MVC's annotated handler mappings
     */
    public ApiEndpointScanner(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings)
    {
        this.mappings = requireNonNull(mappings, "mappings");
    }

    /**
     * Returns every API endpoint, one per method and path, by path and method.
     *
     * @return the endpoints
     * @throws IllegalStateException listing every handler without exactly one access annotation or without an HTTP
     *         method
     */
    public List<DeclaredEndpoint> scan()
    {
        List<DeclaredEndpoint> endpoints = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.getHandlerMethods().entrySet()) {
            List<String> paths = entry.getKey().getPatternValues().stream().filter(path -> path.startsWith(API_PREFIX)).sorted()
                    .toList();
            if (paths.isEmpty()) {
                continue;
            }
            HandlerMethod handler = entry.getValue();
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
            try {
                var declaration = EndpointDeclarations.of(handler);
                if (methods.isEmpty()) {
                    throw new IllegalStateException(declaration.handler() + " does not name its HTTP method");
                }
                for (String path : paths) {
                    methods.stream().sorted().forEach(method -> endpoints.add(new DeclaredEndpoint(method.name(), path, declaration)));
                }
            }
            catch (IllegalStateException problem) {
                problems.add(problem.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("every API endpoint must declare who may call it: "
                    + String.join("; ", problems.stream().sorted().toList()));
        }
        endpoints.sort(Comparator.comparing(DeclaredEndpoint::pathPattern).thenComparing(DeclaredEndpoint::httpMethod));
        return endpoints;
    }
}
