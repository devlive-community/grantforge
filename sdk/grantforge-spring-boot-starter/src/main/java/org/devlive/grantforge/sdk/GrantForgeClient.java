// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.devlive.grantforge.sdk.GrantForgeException.Reason;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Asks GrantForge's open API what a user may do. Answers are kept per token (by its hash, never the token itself) for
 * the cache TTL, then revalidated with {@code If-None-Match}: GrantForge answers 304 while nothing changed, so a user's
 * permissions follow GrantForge within the TTL at little cost. A token GrantForge refuses is forgotten at once.
 */
public final class GrantForgeClient
{
    /** The open API's answer for the token's user. */
    static final String AUTHORIZATION = "/api/v1/open/me/authorization";

    private final RestClient http;
    private final Duration ttl;
    private final Clock clock;
    private final Map<String, Cached> cache;

    /**
     * Creates the client.
     *
     * @param http a client whose base URL is GrantForge's
     * @param ttl how long an answer is used before it is revalidated
     * @param size how many answers are kept, the least recently used going first
     * @param clock the current time
     */
    public GrantForgeClient(RestClient http, Duration ttl, int size, Clock clock)
    {
        this.http = requireNonNull(http, "http");
        this.ttl = requireNonNull(ttl, "ttl");
        this.clock = requireNonNull(clock, "clock");
        this.cache = new LinkedHashMap<>(16, 0.75f, true)
        {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest)
            {
                return size() > size;
            }
        };
    }

    /**
     * Returns what the token's user may do in this application.
     *
     * @param token the user's GrantForge access token, with the {@code permissions} scope
     * @return the user's permissions
     * @throws GrantForgeException {@link Reason#UNAUTHENTICATED} if GrantForge no longer accepts the token,
     *         {@link Reason#FORBIDDEN} if it lacks the scope or is not a user's, {@link Reason#UNAVAILABLE} if GrantForge
     *         does not answer
     */
    public UserAuthorization authorization(String token)
    {
        String key = hash(token);
        Instant now = clock.instant();
        Cached known;
        synchronized (cache) {
            known = cache.get(key);
        }
        if (known != null && known.checkedAt().plus(ttl).isAfter(now)) {
            return known.authorization();
        }
        Cached fresh;
        try {
            fresh = fetch(token, known, now);
        }
        catch (GrantForgeException refused) {
            if (refused.getReason() != Reason.UNAVAILABLE) {
                forgetHash(key);
            }
            throw refused;
        }
        synchronized (cache) {
            cache.put(key, fresh);
        }
        return fresh.authorization();
    }

    /**
     * Forgets what is known of a token, as when its user signs out.
     *
     * @param token the token
     */
    public void forget(String token)
    {
        forgetHash(hash(token));
    }

    private void forgetHash(String key)
    {
        synchronized (cache) {
            cache.remove(key);
        }
    }

    private Cached fetch(String token, @Nullable Cached known, Instant now)
    {
        try {
            return requireNonNull(http.get().uri(AUTHORIZATION).headers(headers -> {
                headers.setBearerAuth(token);
                if (known != null) {
                    headers.setIfNoneMatch(known.etag());
                }
            }).exchange((request, response) -> {
                HttpStatusCode status = response.getStatusCode();
                if (status.value() == HttpStatus.NOT_MODIFIED.value() && known != null) {
                    return new Cached(known.authorization(), known.etag(), now);
                }
                if (status.value() == HttpStatus.UNAUTHORIZED.value()) {
                    throw new GrantForgeException(Reason.UNAUTHENTICATED, "GrantForge refused the token", null);
                }
                if (status.value() == HttpStatus.FORBIDDEN.value()) {
                    throw new GrantForgeException(Reason.FORBIDDEN, "GrantForge refused to answer for the token", null);
                }
                if (!status.is2xxSuccessful()) {
                    throw new GrantForgeException(Reason.UNAVAILABLE, "GrantForge answered " + status.value(), null);
                }
                UserAuthorization answer = response.bodyTo(UserAuthorization.class);
                if (answer == null) {
                    throw new GrantForgeException(Reason.UNAVAILABLE, "GrantForge answered without a body", null);
                }
                String etag = response.getHeaders().getFirst(HttpHeaders.ETAG);
                return new Cached(answer, etag == null ? "\"" + answer.version() + "\"" : etag, now);
            }));
        }
        catch (RestClientException unreachable) {
            throw new GrantForgeException(Reason.UNAVAILABLE, "GrantForge did not answer", unreachable);
        }
    }

    private static String hash(String token)
    {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    /** An answer, its ETag and when GrantForge last confirmed it. */
    private record Cached(UserAuthorization authorization, String etag, Instant checkedAt)
    {
    }
}
