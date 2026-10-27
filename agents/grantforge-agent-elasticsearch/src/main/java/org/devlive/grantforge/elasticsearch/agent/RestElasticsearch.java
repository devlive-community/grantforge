// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.ResponseException;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Elasticsearch through its low-level REST client on HttpClient 5, which goes to the next node when one cannot be
 * reached, signed in with a password, an API key or a client certificate, with JSON read and written by Jackson.
 */
final class RestElasticsearch
        implements Elasticsearch
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger LOG = Logger.getLogger(RestElasticsearch.class.getName());

    private final Rest5Client rest;
    private final PoolingAsyncClientConnectionManager connections;
    private final String where;

    /**
     * Opens a client of the cluster.
     *
     * @param connection the nodes and the agent's sign-in
     * @throws IllegalStateException when a TLS file cannot be read or used
     */
    RestElasticsearch(AgentConfig.Connection connection)
    {
        where = String.join(",", connection.hosts());
        Tls tls = connection.hosts().get(0).startsWith("https:") ? tls(connection) : null;
        Timeout timeout = Timeout.ofMilliseconds(connection.timeout().toMillis());
        PoolingAsyncClientConnectionManagerBuilder pool = PoolingAsyncClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(timeout).setSocketTimeout(timeout).build());
        if (tls != null) {
            // The verifier alone checks names, so that the verification modes certificate and none skip the check.
            pool.setTlsStrategy(ClientTlsStrategyBuilder.create().setSslContext(tls.context()).setHostVerificationPolicy(HostnameVerificationPolicy.CLIENT)
                    .setHostnameVerifier(tls.verifier()).build());
        }
        connections = pool.build();
        HttpHost[] hosts = connection.hosts().stream().map(RestElasticsearch::host).toArray(HttpHost[]::new);
        HttpAsyncClientBuilder client = HttpAsyncClients.custom().setConnectionManager(connections)
                .setDefaultRequestConfig(RequestConfig.custom().setConnectionRequestTimeout(timeout).setResponseTimeout(timeout).build());
        // Every request carries its credentials rather than waiting to be challenged for them.
        String user = connection.user();
        String password = connection.password();
        String apiKey = connection.apiKey();
        Header[] headers = new Header[0];
        if (user != null && password != null) {
            headers = new Header[] {new BasicHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString((user + ":" + password)
                    .getBytes(StandardCharsets.UTF_8)))};
        }
        else if (apiKey != null) {
            headers = new Header[] {new BasicHeader(HttpHeaders.AUTHORIZATION, "ApiKey " + apiKey)};
        }
        rest = Rest5Client.builder(hosts).setHttpClient(client.build()).setDefaultHeaders(headers).build();
    }

    private static HttpHost host(String url)
    {
        URI uri = URI.create(url);
        return new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
    }

    private static Tls tls(AgentConfig.Connection connection)
    {
        try {
            return Tls.of(connection.verificationMode(), path(connection.ca()), path(connection.certificate()), path(connection.key()),
                    connection.keyPassphrase());
        }
        catch (IOException | GeneralSecurityException unusable) {
            throw new IllegalStateException("the agent's TLS files cannot be used: " + unusable.getMessage(), unusable);
        }
    }

    private static @Nullable Path path(@Nullable String file)
    {
        return file == null ? null : Path.of(file);
    }

    @Override
    public JsonNode send(String method, String path, Map<String, String> parameters, @Nullable JsonNode body)
    {
        Request request = new Request(method, path);
        request.addParameters(parameters);
        try {
            if (body != null) {
                request.setEntity(new StringEntity(JSON.writeValueAsString(body), ContentType.APPLICATION_JSON));
            }
            // The client answers what is below 500 itself, refusals among it, and throws only for the rest.
            Response response = rest.performRequest(request);
            int status = response.getStatusCode();
            if (status >= 300) {
                throw new ElasticsearchFailure(method + " " + path + " at " + where + ": " + status + " " + reason(response), status, null);
            }
            String text = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
            return text.isEmpty() ? JSON.createObjectNode() : JSON.readTree(text);
        }
        catch (ResponseException refused) {
            int status = refused.getResponse().getStatusCode();
            throw new ElasticsearchFailure(method + " " + path + " at " + where + ": " + status + " " + reason(refused.getResponse()), status, refused);
        }
        catch (IOException | ParseException failed) {
            throw new ElasticsearchFailure(method + " " + path + " at " + where + ": " + message(failed), 0, failed);
        }
    }

    /** What a refusal's body says: Elasticsearch's {@code error.reason}, its status message, or the body's first line. */
    private static String reason(Response response)
    {
        String fallback = "HTTP " + response.getStatusCode();
        String body;
        try {
            body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
        }
        catch (IOException | ParseException unreadable) {
            return fallback;
        }
        return reason(body, fallback);
    }

    /**
     * Reads what an error response says.
     *
     * @param body the response's body
     * @param fallback what to say when the body says nothing
     * @return {@code error.reason} or {@code message} of a JSON error, the error text of a plain one, or the body's first
     *         line
     */
    static String reason(String body, String fallback)
    {
        String reason = errorReason(body);
        if (!reason.isEmpty()) {
            return firstLine(reason);
        }
        String text = firstLine(body);
        return text.isEmpty() ? fallback : text;
    }

    /** The reason of a JSON error, empty for a plain-text body such as a proxy's "Unauthorized". */
    private static String errorReason(String body)
    {
        try {
            JsonNode answer = JSON.readTree(body);
            JsonNode error = answer.path("error");
            if (error.isTextual()) {
                return error.asText();
            }
            String reason = error.path("reason").asText("");
            return reason.isEmpty() ? answer.path("message").asText("") : reason;
        }
        catch (IOException notJson) {
            return "";
        }
    }

    private static String firstLine(String text)
    {
        int line = text.indexOf('\n');
        return (line < 0 ? text : text.substring(0, line)).strip();
    }

    /** The first line of a failure's message, or of its cause's when it has none, or its kind. */
    static String message(Throwable failure)
    {
        for (Throwable each = failure; each != null; each = each.getCause()) {
            String text = each.getMessage();
            if (text != null && !text.isBlank()) {
                return firstLine(text);
            }
        }
        return failure.getClass().getSimpleName();
    }

    /** Lets go of the connections at once, then of the client; a graceful close of open connections waits seconds for nothing. */
    @Override
    public void close()
    {
        connections.close(CloseMode.IMMEDIATE);
        try {
            rest.close();
        }
        catch (IOException unclosed) {
            LOG.log(Level.FINE, "The Elasticsearch client did not close", unclosed);
        }
    }
}
