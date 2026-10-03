// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.FieldAppearance;
import org.devlive.grantforge.authz.domain.FieldDirection;
import org.devlive.grantforge.persistence.secured.DeclaredField;
import org.devlive.grantforge.persistence.secured.SecuredEntities;
import org.devlive.grantforge.persistence.secured.SecuredEntityDefinition;
import org.devlive.grantforge.persistence.secured.SecuredField;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecuredFieldScannerTest
{
    private static final DeclaredField EMAIL = new DeclaredField("user", "email", "E-mail");

    record Person(String name, @SecuredField(entity = "user", field = "email", name = "E-mail") String email)
    {
    }

    record Change(@SecuredField(entity = "user", field = "email", name = "E-mail") String email)
    {
    }

    record Renamed(@SecuredField(entity = "user", field = "email", name = "Mail") String email)
    {
    }

    record Stranger(@SecuredField(entity = "nobody", field = "secret", name = "Secret") String secret)
    {
    }

    /** Handlers returning and accepting secured fields. */
    static final class Controller
    {
        public List<Person> list()
        {
            return List.of();
        }

        public ResponseEntity<Person> update(@PathVariable String id, @RequestBody Change change)
        {
            return ResponseEntity.ok(new Person(id, change.email()));
        }

        public void plain(String text)
        {
        }

        public Renamed renamed()
        {
            return new Renamed("");
        }

        public Stranger stranger()
        {
            return new Stranger("");
        }
    }

    private static HandlerMethod handler(String method) throws NoSuchMethodException
    {
        for (var candidate : Controller.class.getMethods()) {
            if (candidate.getName().equals(method)) {
                return new HandlerMethod(new Controller(), candidate);
            }
        }
        throw new NoSuchMethodException(method);
    }

    private static RequestMappingInfo mapping(RequestMethod method, String... paths)
    {
        return RequestMappingInfo.paths(paths).methods(method).build();
    }

    private static SecuredFieldScanner scanner(Map<RequestMappingInfo, HandlerMethod> handlers)
    {
        RequestMappingHandlerMapping mappings = mock(RequestMappingHandlerMapping.class);
        when(mappings.getHandlerMethods()).thenReturn(handlers);
        SecuredEntities entities = mock(SecuredEntities.class);
        when(entities.find(anyString())).thenReturn(Optional.empty());
        when(entities.find("user")).thenReturn(Optional.of(new SecuredEntityDefinition("user", "Users", Object.class, List.of(),
                null, null, false, true, null)));
        return new SecuredFieldScanner(mappings, entities);
    }

    @Test
    void listsWhereApisReturnAndAcceptSecuredFields() throws NoSuchMethodException
    {
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        handlers.put(mapping(RequestMethod.PUT, "/api/v1/users/{id}"), handler("update"));
        handlers.put(mapping(RequestMethod.GET, "/api/v1/users", "/console/users"), handler("list"));
        handlers.put(mapping(RequestMethod.GET, "/api/v1/plain"), handler("plain"));

        assertThat(scanner(handlers).scan()).containsExactly(
                new FieldAppearance(EMAIL, "GET", "/api/v1/users", FieldDirection.READ),
                new FieldAppearance(EMAIL, "PUT", "/api/v1/users/{id}", FieldDirection.READ),
                new FieldAppearance(EMAIL, "PUT", "/api/v1/users/{id}", FieldDirection.WRITE));
    }

    @Test
    void refusesFieldsOfUnknownEntitiesAndFieldsNamedTwoWays() throws NoSuchMethodException
    {
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        handlers.put(mapping(RequestMethod.GET, "/api/v1/users"), handler("list"));
        handlers.put(mapping(RequestMethod.GET, "/api/v1/renamed"), handler("renamed"));
        handlers.put(mapping(RequestMethod.GET, "/api/v1/stranger"), handler("stranger"));

        assertThatIllegalStateException().isThrownBy(() -> scanner(handlers).scan())
                .withMessageContaining("entity:user.email is named E-mail and Mail")
                .withMessageContaining("entity:nobody.secret names no secured entity");
    }
}
