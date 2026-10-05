// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.perf;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * Calls the console API like the console does: the session cookie signs in, and requests that change something carry
 * the {@code XSRF-TOKEN} cookie in the {@code X-XSRF-TOKEN} header.
 */
final class ConsoleClient
{
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final URI base;
    private final CookieManager cookies = new CookieManager();
    private final HttpClient http;

    /**
     * Creates a client without a session; the first request receives the CSRF cookie.
     *
     * @param base the server, such as {@code http://127.0.0.1:8080}
     */
    ConsoleClient(URI base)
    {
        this.base = requireNonNull(base, "base");
        this.http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(TIMEOUT).build();
    }

    /**
     * Signs in.
     *
     * @param username the user name
     * @param password the password
     * @throws IOException if the server cannot be reached or refuses
     * @throws InterruptedException if interrupted
     */
    void signIn(String username, String password) throws IOException, InterruptedException
    {
        get("/api/v1/bootstrap");
        post("/api/v1/auth/login", "{\"username\": " + json(username) + ", \"password\": " + json(password) + "}");
    }

    /**
     * Reads a resource.
     *
     * @param path the path and query
     * @return the response body
     * @throws IOException if the server cannot be reached or answers with an error
     * @throws InterruptedException if interrupted
     */
    String get(String path) throws IOException, InterruptedException
    {
        return send(HttpRequest.newBuilder(base.resolve(path)).timeout(TIMEOUT).GET().header("Accept", "application/json").build());
    }

    /**
     * Sends JSON.
     *
     * @param path the path
     * @param body the JSON body
     * @return the response body
     * @throws IOException if the server cannot be reached or answers with an error
     * @throws InterruptedException if interrupted
     */
    String post(String path, String body) throws IOException, InterruptedException
    {
        return send(HttpRequest.newBuilder(base.resolve(path)).timeout(TIMEOUT).POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json").header("Accept", "application/json").header("X-XSRF-TOKEN", xsrf()).build());
    }

    private String send(HttpRequest request) throws IOException, InterruptedException
    {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException(request.method() + " " + request.uri().getPath() + " answered " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private String xsrf()
    {
        return cookies.getCookieStore().get(base).stream().filter(cookie -> "XSRF-TOKEN".equals(cookie.getName())).map(HttpCookie::getValue)
                .findFirst().orElse("");
    }

    /**
     * Quotes text as a JSON string.
     *
     * @param text the text, without control characters
     * @return the JSON string
     */
    static String json(String text)
    {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
