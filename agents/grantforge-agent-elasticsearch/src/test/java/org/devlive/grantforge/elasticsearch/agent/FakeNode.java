// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A stand-in for an Elasticsearch node: the JDK's HTTP or HTTPS server answering each path with a status and a body, as
 * set, and 404 otherwise. Like Elasticsearch with security on, it asks for a password with a Basic challenge when one is set and a
 * request brings none, and it records the paths, queries and users of the requests it was sent. HttpClient sends a
 * password it has at once, without waiting for the challenge.
 */
final class FakeNode
        implements AutoCloseable
{
    /**
     * A request the node was sent.
     *
     * @param path the path, decoded
     * @param rawPath the path as sent
     * @param query the query, or {@code null}
     * @param user the user of its Basic authorization, or {@code null}
     */
    record Sent(String path, String rawPath, @Nullable String query, @Nullable String user)
    {
    }

    private record Answer(int status, String body)
    {
    }

    private final HttpServer server;
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private volatile @Nullable String password;

    private FakeNode(HttpServer server)
    {
        this.server = server;
        server.createContext("/", this::answer);
        server.start();
    }

    /** Starts a node over plain HTTP on the loopback address. */
    static FakeNode http() throws IOException
    {
        return new FakeNode(HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0));
    }

    /**
     * Starts a node over HTTPS on the loopback address.
     *
     * @param tls its TLS context
     * @param clientCertificate whether it wants a client certificate
     */
    static FakeNode https(SSLContext tls, boolean clientCertificate) throws IOException
    {
        HttpsServer server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(tls)
        {
            @Override
            public void configure(HttpsParameters parameters)
            {
                SSLParameters wanted = tls.getDefaultSSLParameters();
                wanted.setNeedClientAuth(clientCertificate);
                parameters.setSSLParameters(wanted);
            }
        });
        return new FakeNode(server);
    }

    /** The node's port. */
    int port()
    {
        return server.getAddress().getPort();
    }

    /**
     * Sets the answer of a path.
     *
     * @param path the decoded path
     * @param status the status
     * @param body the body
     * @return this node
     */
    FakeNode answer(String path, int status, String body)
    {
        answers.put(path, new Answer(status, body));
        return this;
    }

    /**
     * Wants a password of every request, as the security plugin does.
     *
     * @param value the password any user signs in with
     * @return this node
     */
    FakeNode password(String value)
    {
        password = value;
        return this;
    }

    /** The requests the node answered, in order. */
    List<Sent> sent()
    {
        return new ArrayList<>(sent);
    }

    private void answer(HttpExchange exchange) throws IOException
    {
        try (exchange) {
            String user = null;
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (authorization != null && authorization.startsWith("Basic ")) {
                String pair = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
                user = pair.substring(0, pair.indexOf(':'));
                if (password != null && !pair.substring(pair.indexOf(':') + 1).equals(password)) {
                    respond(exchange, 401, "Unauthorized");
                    return;
                }
            }
            sent.add(new Sent(exchange.getRequestURI().getPath(), exchange.getRequestURI().getRawPath(), exchange.getRequestURI().getQuery(), user));
            if (password != null && user == null) {
                exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"Elasticsearch Security\"");
                respond(exchange, 401, "Unauthorized");
                return;
            }
            Answer found = answers.get(exchange.getRequestURI().getPath());
            if (found == null) {
                respond(exchange, 404, "{\"error\":{\"type\":\"index_not_found_exception\",\"reason\":\"no such index\"},\"status\":404}");
            }
            else {
                respond(exchange, found.status(), found.body());
            }
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException
    {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", body.startsWith("{") ? "application/json" : "text/plain");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    @Override
    public void close()
    {
        server.stop(0);
    }
}
