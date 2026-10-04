// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.perf;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsoleClientTest
{
    private final List<String> seen = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @BeforeEach
    void start() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/bootstrap", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "XSRF-TOKEN=token-1; Path=/");
            answer(exchange, 200, "{}");
        });
        server.createContext("/api/v1/auth/login", exchange -> {
            seen.add(exchange.getRequestMethod() + " " + exchange.getRequestHeaders().getFirst("X-XSRF-TOKEN") + " "
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Set-Cookie", "GRANTFORGE_SESSION=s1; Path=/; HttpOnly");
            answer(exchange, 200, "{\"username\": \"admin\"}");
        });
        server.createContext("/api/v1/users", exchange -> {
            seen.add("users " + exchange.getRequestHeaders().getFirst("Cookie"));
            answer(exchange, 200, "{\"total\": 3}");
        });
        server.createContext("/api/v1/denied", exchange -> answer(exchange, 403, "{\"code\": \"GF-SECURITY-001\"}"));
        server.start();
    }

    @AfterEach
    void stop()
    {
        server.stop(0);
    }

    private static void answer(HttpExchange exchange, int status, String body) throws IOException
    {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    void signsInWithTheCsrfTokenAndKeepsTheSession() throws Exception
    {
        ConsoleClient client = new ConsoleClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));

        client.signIn("admin", "pa\"ss");

        assertThat(client.get("/api/v1/users?page=1")).isEqualTo("{\"total\": 3}");
        assertThat(seen).first().isEqualTo("POST token-1 {\"username\": \"admin\", \"password\": \"pa\\\"ss\"}");
        assertThat(seen.get(1)).contains("GRANTFORGE_SESSION=s1");
        assertThatThrownBy(() -> client.get("/api/v1/denied")).isInstanceOf(IOException.class).hasMessageContaining("403");
        assertThatThrownBy(() -> client.post("/api/v1/denied", "{}")).isInstanceOf(IOException.class);
    }

    @Test
    void quotesJsonStrings()
    {
        assertThat(ConsoleClient.json("a\\b\"c")).isEqualTo("\"a\\\\b\\\"c\"");
    }
}
