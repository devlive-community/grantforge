// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An OpenID Connect provider for tests: it publishes a discovery document and its key, and its token endpoint answers any
 * code with an ID token for the user and nonce the test chose.
 */
final class FakeProvider
        implements AutoCloseable
{
    private final HttpServer server;
    private final RSAKey key;
    private final List<String> tokenRequests = new CopyOnWriteArrayList<>();
    private final Map<String, Object> claims = new ConcurrentHashMap<>();
    private volatile String nonce = "";

    private FakeProvider(HttpServer server, RSAKey key)
    {
        this.server = server;
        this.key = key;
    }

    /**
     * Starts a provider on a free port.
     *
     * @return the provider
     * @throws Exception if it cannot start
     */
    static FakeProvider start() throws Exception
    {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        FakeProvider provider = new FakeProvider(server, new RSAKeyGenerator(2048).keyID("fake").generate());
        server.createContext("/.well-known/openid-configuration", exchange -> provider.json(exchange, """
                {"issuer": "%1$s", "authorization_endpoint": "%1$s/authorize", "token_endpoint": "%1$s/token", "jwks_uri": "%1$s/jwks",
                 "response_types_supported": ["code"], "subject_types_supported": ["public"],
                 "id_token_signing_alg_values_supported": ["RS256"]}
                """.formatted(provider.issuer())));
        server.createContext("/jwks", exchange -> provider.json(exchange, new JWKSet(provider.key.toPublicJWK()).toString()));
        server.createContext("/token", provider::token);
        server.start();
        return provider;
    }

    /**
     * Returns the issuer.
     *
     * @return {@code http://localhost:<port>}
     */
    String issuer()
    {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /**
     * Chooses who the next ID token describes.
     *
     * @param subject the subject
     * @param username the preferred user name, or {@code null} for none
     * @param name the display name
     * @param requestNonce the nonce of the authorization request
     */
    void signIn(String subject, @Nullable String username, String name, String requestNonce)
    {
        claims.clear();
        claims.put("sub", subject);
        if (username != null) {
            claims.put("preferred_username", username);
        }
        claims.put("name", name);
        nonce = requestNonce;
    }

    /**
     * Returns the bodies of the token requests so far.
     *
     * @return the requests
     */
    List<String> tokenRequests()
    {
        return List.copyOf(tokenRequests);
    }

    private void token(HttpExchange exchange) throws IOException
    {
        tokenRequests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8) + " auth="
                + exchange.getRequestHeaders().getFirst("Authorization"));
        Instant now = Instant.now();
        JWTClaimsSet.Builder token = new JWTClaimsSet.Builder().issuer(issuer()).audience("grantforge").issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300))).claim("nonce", nonce);
        claims.forEach(token::claim);
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), token.build());
        try {
            jwt.sign(new RSASSASigner(key));
        }
        catch (JOSEException impossible) {
            throw new IOException(impossible);
        }
        json(exchange, """
                {"access_token": "at", "token_type": "Bearer", "expires_in": 300, "scope": "openid", "id_token": "%s"}
                """.formatted(jwt.serialize()));
    }

    private void json(HttpExchange exchange, String body) throws IOException
    {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close()
    {
        server.stop(0);
    }
}
