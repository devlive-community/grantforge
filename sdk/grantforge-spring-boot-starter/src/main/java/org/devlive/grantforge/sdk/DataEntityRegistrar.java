// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Declares the application's {@link GrantForgeEntity} entities to GrantForge, with a token of the application's own client
 * (client credentials, {@code catalog} scope). Declaring again replaces the declaration, so it runs at every start. A
 * failure is logged, not fatal: the application keeps its last declaration, and rules about new entities wait until the
 * next start.
 */
public final class DataEntityRegistrar
{
    /** Where entities are declared. */
    static final String DECLARE = "/api/v1/open/catalog/data-entities";

    private static final Logger LOG = LoggerFactory.getLogger(DataEntityRegistrar.class);

    private final RestClient http;
    private final String clientId;
    private final String clientSecret;

    /**
     * Creates the registrar.
     *
     * @param http a client whose base URL is GrantForge's
     * @param clientId the application's client ID
     * @param clientSecret the client's secret
     */
    public DataEntityRegistrar(RestClient http, String clientId, String clientSecret)
    {
        this.http = requireNonNull(http, "http");
        this.clientId = requireNonNull(clientId, "clientId");
        this.clientSecret = requireNonNull(clientSecret, "clientSecret");
    }

    /**
     * Declares entities.
     *
     * @param types the entity classes
     * @return whether GrantForge took them in
     */
    public boolean declare(Collection<Class<?>> types)
    {
        EntityDeclarations.Request request = EntityDeclarations.of(types);
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("scope", "catalog");
            Map<?, ?> token = http.post().uri("/oauth2/token").headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
            Object accessToken = token == null ? null : token.get("access_token");
            if (accessToken == null) {
                LOG.warn("GrantForge issued no token to declare data entities");
                return false;
            }
            http.put().uri(DECLARE).headers(headers -> headers.setBearerAuth(accessToken.toString())).contentType(MediaType.APPLICATION_JSON)
                    .body(request).retrieve().toBodilessEntity();
            LOG.info("Declared {} data entities to GrantForge: {}", request.entities().size(),
                    request.entities().stream().map(EntityDeclarations.Entity::code).toList());
            return true;
        }
        catch (RestClientException refused) {
            LOG.warn("Could not declare data entities to GrantForge: {}", refused.getMessage());
            return false;
        }
    }

    /**
     * Returns the annotated classes among some.
     *
     * @param classes the classes, such as the JPA metamodel's entities
     * @return those annotated with {@link GrantForgeEntity}
     */
    public static List<Class<?>> annotated(Collection<Class<?>> classes)
    {
        return classes.stream().filter(type -> type.isAnnotationPresent(GrantForgeEntity.class)).toList();
    }
}
