// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.devlive.grantforge.authz.domain.OAuthClient;
import org.devlive.grantforge.authz.domain.OAuthClientRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Lets browser applications call the authorization server and the open API from their own origin (D-68): an origin is
 * allowed while an enabled client has a redirect URI on it, so registering a client is all an application needs. Calls
 * carry bearer tokens, never cookies, so credentials are not allowed; ETags are exposed for conditional requests. Each
 * node rereads the clients every minute.
 */
@Component
public final class ClientOrigins
        implements CorsConfigurationSource
{
    /** How long a node uses the origins it read. */
    static final Duration REFRESH = Duration.ofMinutes(1);

    private static final long MAX_AGE = 600;

    private final OAuthClientRepository clients;
    private final Clock clock;
    private final AtomicReference<@Nullable Known> known = new AtomicReference<>();

    /**
     * Creates the source.
     *
     * @param clients the catalog's clients
     * @param clock the current time
     */
    public ClientOrigins(OAuthClientRepository clients, Clock clock)
    {
        this.clients = requireNonNull(clients, "clients");
        this.clock = requireNonNull(clock, "clock");
    }

    @Override
    public @Nullable CorsConfiguration getCorsConfiguration(HttpServletRequest request)
    {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || !origins().contains(origin.toLowerCase(Locale.ROOT))) {
            return null;
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(origin));
        configuration.setAllowedMethods(List.of("GET", "POST"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.IF_NONE_MATCH));
        configuration.setExposedHeaders(List.of(HttpHeaders.ETAG));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(MAX_AGE);
        return configuration;
    }

    /**
     * Returns the origins of the redirect URIs of enabled clients.
     *
     * @return the origins, lowercase, such as {@code https://app.example.com}
     */
    Set<String> origins()
    {
        Instant now = clock.instant();
        Known current = known.get();
        if (current != null && current.readAt().plus(REFRESH).isAfter(now)) {
            return current.origins();
        }
        Set<String> origins = clients.findAll().stream().filter(OAuthClient::isEnabled).flatMap(client -> client.getRedirectUris().stream())
                .map(ClientOrigins::originOf).filter(origin -> origin != null).map(origin -> requireNonNull(origin))
                .collect(Collectors.toUnmodifiableSet());
        known.set(new Known(origins, now));
        return origins;
    }

    /** The origin of a web redirect URI; native apps' own schemes have none a browser sends. */
    static @Nullable String originOf(String redirectUri)
    {
        URI uri = URI.create(redirectUri);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !"http".equals(scheme) || uri.getHost() == null) {
            return null;
        }
        String origin = scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT);
        return uri.getPort() < 0 ? origin : origin + ":" + uri.getPort();
    }

    /** Origins as one node read them. */
    private record Known(Set<String> origins, Instant readAt)
    {
    }
}
