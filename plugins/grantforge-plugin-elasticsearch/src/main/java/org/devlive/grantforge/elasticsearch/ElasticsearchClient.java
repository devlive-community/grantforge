// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch;

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
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.util.Timeout;
import org.devlive.grantforge.plugin.api.LookupException;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A client of one Elasticsearch cluster: Elasticsearch's own low-level REST client on HttpClient 5, which goes to the
 * next node when one cannot be reached, signed in with a password, an API key or a client certificate, and reading JSON
 * responses with Jackson.
 * Failures become lookup failures the console can name: refused sign-ins, missing permissions, unknown names, nodes
 * that cannot be reached and certificates that cannot be verified.
 */
final class ElasticsearchClient
        implements AutoCloseable
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger LOG = Logger.getLogger(ElasticsearchClient.class.getName());

    private final Rest5Client rest;
    private final ModalCloseable connections;
    private final String where;

    ElasticsearchClient(Rest5Client rest, ModalCloseable connections, String where)
    {
        this.rest = rest;
        this.connections = connections;
        this.where = where;
    }

    /**
     * Opens a client of a service's nodes.
     *
     * @param settings the service's settings
     * @return the client; close it when done
     * @throws LookupException when a TLS file cannot be read or used
     */
    static ElasticsearchClient open(ElasticsearchSettings settings)
    {
        String where = String.join(",", settings.hosts());
        HttpHost[] hosts = settings.hosts().stream().map(ElasticsearchClient::host).toArray(HttpHost[]::new);
        ElasticsearchTls tls = settings.secure() ? tls(settings) : null;
        Timeout timeout = Timeout.ofMilliseconds(settings.timeout());
        PoolingAsyncClientConnectionManagerBuilder pool = PoolingAsyncClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(timeout).setSocketTimeout(timeout).build());
        if (tls != null) {
            // The verifier alone checks names, so that the verification modes certificate and none skip the check.
            pool.setTlsStrategy(ClientTlsStrategyBuilder.create().setSslContext(tls.context()).setHostVerificationPolicy(HostnameVerificationPolicy.CLIENT)
                    .setHostnameVerifier(tls.verifier()).build());
        }
        PoolingAsyncClientConnectionManager connections = pool.build();
        HttpAsyncClientBuilder client = HttpAsyncClients.custom().setConnectionManager(connections)
                .setDefaultRequestConfig(RequestConfig.custom().setConnectionRequestTimeout(timeout).setResponseTimeout(timeout).build());
        // Every request carries its credentials, as Kibana's do, rather than waiting to be challenged for them.
        String user = settings.user();
        String password = settings.password();
        String apiKey = settings.apiKey();
        Header[] headers = new Header[0];
        if (ElasticsearchSettings.PASSWORD_SIGN_IN.equals(settings.authentication()) && user != null && password != null) {
            headers = new Header[] {new BasicHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString((user + ":" + password)
                    .getBytes(StandardCharsets.UTF_8)))};
        }
        else if (ElasticsearchSettings.API_KEY_SIGN_IN.equals(settings.authentication()) && apiKey != null) {
            headers = new Header[] {new BasicHeader(HttpHeaders.AUTHORIZATION, "ApiKey " + apiKey)};
        }
        return new ElasticsearchClient(Rest5Client.builder(hosts).setHttpClient(client.build()).setDefaultHeaders(headers).build(), connections, where);
    }

    private static HttpHost host(String url)
    {
        URI uri = URI.create(url);
        return new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
    }

    private static ElasticsearchTls tls(ElasticsearchSettings settings)
    {
        try {
            return ElasticsearchTls.of(settings.verificationMode(), path(settings.certificateAuthorities()), path(settings.certificate()),
                    path(settings.key()), settings.keyPassphrase());
        }
        catch (IOException unreadable) {
            throw new LookupException(LookupException.Reason.FAILED, "a TLS file of the service cannot be read: " + message(unreadable), unreadable);
        }
        catch (GeneralSecurityException unusable) {
            throw new LookupException(LookupException.Reason.FAILED, "a TLS file of the service cannot be used: " + message(unusable), unusable);
        }
    }

    private static @Nullable Path path(@Nullable String file)
    {
        return file == null ? null : Path.of(file);
    }

    /**
     * Reads a resource of the cluster.
     *
     * @param endpoint the path, its names already encoded
     * @param parameters the query parameters
     * @return the response's JSON
     * @throws LookupException when the request fails, naming why
     */
    JsonNode get(String endpoint, Map<String, String> parameters)
    {
        JsonNode found = read(endpoint, parameters, false);
        if (found == null) {
            throw new IllegalStateException("a required read returned nothing");
        }
        return found;
    }

    /**
     * Reads a resource of the cluster that may not exist, such as the API of a plugin the cluster may not have.
     *
     * @param endpoint the path, its names already encoded
     * @param parameters the query parameters
     * @return the response's JSON, or {@code null} when the cluster has no such resource or no handler of the path
     * @throws LookupException when the request fails otherwise, naming why
     */
    @Nullable JsonNode find(String endpoint, Map<String, String> parameters)
    {
        return read(endpoint, parameters, true);
    }

    private @Nullable JsonNode read(String endpoint, Map<String, String> parameters, boolean optional)
    {
        Request request = new Request("GET", endpoint);
        request.addParameters(parameters);
        try {
            // The client answers what is below 500 itself, refusals among it, and throws only for the rest.
            Response response = rest.performRequest(request);
            if (response.getStatusCode() >= 300) {
                return refused(response, optional, null);
            }
            return JSON.readTree(EntityUtils.toString(response.getEntity()));
        }
        catch (ResponseException refused) {
            return refused(refused.getResponse(), optional, refused);
        }
        catch (SSLException untrusted) {
            throw new LookupException(LookupException.Reason.FAILED, where + ": TLS failed: " + message(untrusted), untrusted);
        }
        catch (ConnectException | NoRouteToHostException | UnknownHostException | SocketTimeoutException unreachable) {
            throw new LookupException(LookupException.Reason.UNREACHABLE, where + ": " + message(unreachable), unreachable);
        }
        catch (IOException | ParseException failed) {
            throw failure(failed);
        }
    }

    /** Reads a refusal as nothing found, when what may be missing is, or as the lookup failure it is. */
    private @Nullable JsonNode refused(Response response, boolean optional, @Nullable Exception cause)
    {
        int status = response.getStatusCode();
        String reason = reason(response);
        if (optional && (status == 404 || status == 400 && reason.startsWith("no handler found"))) {
            return null;
        }
        throw new LookupException(reason(status), where + ": " + status + " " + reason, cause);
    }

    /** Names a failure that the client wrapped, by what it wrapped. */
    private LookupException failure(Exception failed)
    {
        Throwable cause = failed.getCause();
        if (cause instanceof SSLException untrusted) {
            return new LookupException(LookupException.Reason.FAILED, where + ": TLS failed: " + message(untrusted), failed);
        }
        if (cause instanceof ConnectException || cause instanceof UnknownHostException || cause instanceof TimeoutException) {
            return new LookupException(LookupException.Reason.UNREACHABLE, where + ": " + message(cause), failed);
        }
        return new LookupException(LookupException.Reason.FAILED, where + ": " + message(failed), failed);
    }

    /**
     * Names the reason of a refused request from its HTTP status.
     *
     * @param status the status
     * @return the reason
     */
    static LookupException.Reason reason(int status)
    {
        return switch (status) {
            case 401 -> LookupException.Reason.AUTHENTICATION_FAILED;
            case 403 -> LookupException.Reason.ACCESS_DENIED;
            case 404 -> LookupException.Reason.NOT_FOUND;
            case 429, 502, 503, 504 -> LookupException.Reason.UNREACHABLE;
            default -> LookupException.Reason.FAILED;
        };
    }

    /** What a refusal's body says: Elasticsearch's {@code error.reason}, or the body's first line. */
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
     * @return {@code error.reason} of a JSON error, the error text of a plain one, or the body's first line
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
            JsonNode error = JSON.readTree(body).path("error");
            return error.isTextual() ? error.asText() : error.path("reason").asText("");
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

    /** The first line of a failure's message, or its kind without one. */
    static String message(Throwable failure)
    {
        String text = failure.getMessage();
        return text == null || text.isBlank() ? failure.getClass().getSimpleName() : firstLine(text);
    }

    /**
     * Lets go of the connections at once, then of the client: every request has had its response, and a graceful close
     * of open connections waits seconds for nothing.
     */
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
