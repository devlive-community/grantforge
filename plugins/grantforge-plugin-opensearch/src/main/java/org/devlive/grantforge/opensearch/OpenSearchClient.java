// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.util.Timeout;
import org.devlive.grantforge.plugin.api.LookupException;
import org.jspecify.annotations.Nullable;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;

import javax.net.ssl.SSLException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A client of one OpenSearch cluster: OpenSearch's own low-level REST client, which goes to the next node when one
 * cannot be reached, signed in with a password or a client certificate, and reading JSON responses with Jackson.
 * Failures become lookup failures the console can name: refused sign-ins, missing permissions, unknown names, nodes
 * that cannot be reached and certificates that cannot be verified.
 */
final class OpenSearchClient
        implements AutoCloseable
{
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger LOG = Logger.getLogger(OpenSearchClient.class.getName());

    private final RestClient rest;
    private final ModalCloseable connections;
    private final String where;

    OpenSearchClient(RestClient rest, ModalCloseable connections, String where)
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
    static OpenSearchClient open(OpenSearchSettings settings)
    {
        String where = String.join(",", settings.hosts());
        HttpHost[] hosts = settings.hosts().stream().map(OpenSearchClient::host).toArray(HttpHost[]::new);
        OpenSearchTls tls = settings.secure() ? tls(settings) : null;
        Timeout timeout = Timeout.ofMilliseconds(settings.timeout());
        PoolingAsyncClientConnectionManagerBuilder pool = PoolingAsyncClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(timeout).setSocketTimeout(timeout).build());
        if (tls != null) {
            // The verifier alone checks names, so that the verification modes certificate and none skip the check.
            pool.setTlsStrategy(ClientTlsStrategyBuilder.create().setSslContext(tls.context()).setHostVerificationPolicy(HostnameVerificationPolicy.CLIENT)
                    .setHostnameVerifier(tls.verifier()).build());
        }
        PoolingAsyncClientConnectionManager connections = pool.build();
        RestClientBuilder builder = RestClient.builder(hosts)
                .setRequestConfigCallback(request -> request.setConnectionRequestTimeout(timeout).setResponseTimeout(timeout))
                .setHttpClientConfigCallback(client -> {
                    client.setConnectionManager(connections);
                    String user = settings.user();
                    String password = settings.password();
                    if (OpenSearchSettings.PASSWORD_SIGN_IN.equals(settings.authentication()) && user != null && password != null) {
                        BasicCredentialsProvider credentials = new BasicCredentialsProvider();
                        credentials.setCredentials(new AuthScope(null, -1), new UsernamePasswordCredentials(user, password.toCharArray()));
                        client.setDefaultCredentialsProvider(credentials);
                    }
                    return client;
                });
        return new OpenSearchClient(builder.build(), connections, where);
    }

    private static HttpHost host(String url)
    {
        URI uri = URI.create(url);
        return new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
    }

    private static OpenSearchTls tls(OpenSearchSettings settings)
    {
        try {
            return OpenSearchTls.of(settings.verificationMode(), path(settings.certificateAuthorities()), path(settings.certificate()),
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
            Response response = rest.performRequest(request);
            return JSON.readTree(EntityUtils.toString(response.getEntity()));
        }
        catch (ResponseException refused) {
            int status = refused.getResponse().getStatusLine().getStatusCode();
            String reason = reason(refused.getResponse());
            if (optional && (status == 404 || status == 400 && reason.startsWith("no handler found"))) {
                return null;
            }
            throw new LookupException(reason(status), where + ": " + status + " " + reason, refused);
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

    /** What a refusal's body says: OpenSearch's {@code error.reason}, or the body's first line. */
    private static String reason(Response response)
    {
        String body;
        try {
            body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
        }
        catch (IOException | ParseException unreadable) {
            return response.getStatusLine().getReasonPhrase();
        }
        return reason(body, response.getStatusLine().getReasonPhrase());
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

    /** The reason of a JSON error, empty for a plain-text body such as the security plugin's "Unauthorized". */
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
            LOG.log(Level.FINE, "The OpenSearch client did not close", unclosed);
        }
    }
}
