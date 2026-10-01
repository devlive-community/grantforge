// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.DeclaredEndpoint;
import org.devlive.grantforge.authz.domain.EndpointAccess;
import org.devlive.grantforge.common.security.PublicEndpoint;
import org.devlive.grantforge.common.security.RequirePermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiEndpointScannerTest
{
    /** Handlers with and without declarations. */
    static final class Controller
    {
        @RequirePermission("system.user.read")
        public void read()
        {
        }

        @PublicEndpoint
        public void open()
        {
        }

        public void forgotten()
        {
        }
    }

    private static HandlerMethod handler(String method) throws NoSuchMethodException
    {
        return new HandlerMethod(new Controller(), Controller.class.getMethod(method));
    }

    private static RequestMappingInfo mapping(RequestMethod[] methods, String... paths)
    {
        return RequestMappingInfo.paths(paths).methods(methods).build();
    }

    private static ApiEndpointScanner scanner(Map<RequestMappingInfo, HandlerMethod> handlers)
    {
        RequestMappingHandlerMapping mappings = mock(RequestMappingHandlerMapping.class);
        when(mappings.getHandlerMethods()).thenReturn(handlers);
        return new ApiEndpointScanner(mappings);
    }

    @Test
    void listsEveryMethodAndPathOfTheApiSorted() throws NoSuchMethodException
    {
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        handlers.put(mapping(new RequestMethod[] {RequestMethod.POST, RequestMethod.GET}, "/api/v1/users", "/api/v1/people"),
                handler("read"));
        handlers.put(mapping(new RequestMethod[] {RequestMethod.GET}, "/api/v1/bootstrap"), handler("open"));
        // Outside the API: not catalogued, and needs no declaration.
        handlers.put(mapping(new RequestMethod[] {RequestMethod.GET}, "/v3/api-docs"), handler("forgotten"));

        List<DeclaredEndpoint> endpoints = scanner(handlers).scan();

        assertThat(endpoints).extracting(DeclaredEndpoint::route).containsExactly("GET /api/v1/bootstrap", "GET /api/v1/people",
                "POST /api/v1/people", "GET /api/v1/users", "POST /api/v1/users");
        assertThat(endpoints.get(0).declaration().access()).isEqualTo(EndpointAccess.PUBLIC);
        assertThat(endpoints.get(1).declaration().permission()).isEqualTo("system.user.read");
    }

    @Test
    void refusesUndeclaredHandlersAndMissingMethods() throws NoSuchMethodException
    {
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        handlers.put(mapping(new RequestMethod[] {RequestMethod.GET}, "/api/v1/forgotten"), handler("forgotten"));
        handlers.put(mapping(new RequestMethod[0], "/api/v1/any"), handler("open"));

        assertThatThrownBy(() -> scanner(handlers).scan()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Controller#forgotten has no access annotation")
                .hasMessageContaining("Controller#open does not name its HTTP method");
    }
}
