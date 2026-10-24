// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.solr;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.request.GenericSolrRequest;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.devlive.grantforge.plugin.api.LookupException;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * Runs work against a Solr cluster through SolrJ's JDK HTTP client, opened for it and closed after it, signed in as the
 * lookup user. The nodes are tried in turn: work goes to the next when a node cannot be reached, and fails when none can.
 * Failures are named by their kind: a refused sign-in, missing permissions, a node that cannot be reached, or something
 * that does not exist.
 */
final class SolrNodeClient
{
    /** Opens a client of one node; SolrJ's own, or a stand-in in tests. */
    @FunctionalInterface
    interface Opener
    {
        /**
         * Opens a client.
         *
         * @param url the node's base URL
         * @param settings the service's settings
         * @return the client
         * @throws IOException when the trust store cannot be read
         * @throws GeneralSecurityException when the trust store cannot be used
         */
        SolrClient open(String url, SolrSettings settings) throws IOException, GeneralSecurityException;
    }

    /**
     * Work with one node.
     *
     * @param <T> what it returns
     */
    @FunctionalInterface
    interface Work<T>
    {
        /**
         * Does the work.
         *
         * @param node sends the node requests, signed in
         * @return the result
         * @throws SolrServerException when the node cannot be asked
         * @throws IOException when the request fails on the way
         */
        T run(Node node) throws SolrServerException, IOException;
    }

    /** Sends one node requests as the lookup user. */
    @FunctionalInterface
    interface Node
    {
        /**
         * Sends a GET request.
         *
         * @param path such as {@code /admin/collections}
         * @param params its parameters
         * @param collection the collection the path is in, or {@code null} for the node's own paths
         * @return the response
         * @throws SolrServerException when the node cannot be asked
         * @throws IOException when the request fails on the way
         */
        NamedList<Object> get(String path, SolrParams params, @Nullable String collection) throws SolrServerException, IOException;
    }

    private final SolrSettings settings;
    private final Opener opener;

    /**
     * Creates a client of a service with SolrJ's own client.
     *
     * @param settings the service's settings
     */
    SolrNodeClient(SolrSettings settings)
    {
        this(settings, SolrNodeClient::open);
    }

    SolrNodeClient(SolrSettings settings, Opener opener)
    {
        this.settings = requireNonNull(settings, "settings");
        this.opener = requireNonNull(opener, "opener");
    }

    /**
     * Runs work with the first node that can be reached.
     *
     * @param work the work
     * @param <T> what it returns
     * @return its result
     * @throws LookupException when the work fails, naming why
     */
    <T> T run(Work<T> work)
    {
        LookupException unreachable = null;
        for (String url : settings.urls()) {
            try (SolrClient client = opener.open(url, settings)) {
                return work.run((path, params, collection) -> client.request(request(path, params, collection), collection));
            }
            catch (SolrServerException | IOException | GeneralSecurityException | RuntimeException failed) {
                LookupException named = failure(failed);
                if (named.getReason() != LookupException.Reason.UNREACHABLE) {
                    throw named;
                }
                unreachable = named;
            }
        }
        throw requireNonNull(unreachable, "no node was asked");
    }

    /**
     * Builds a GET request as the lookup user, with the token of a JSON web token sign-in.
     *
     * @param path such as {@code /admin/collections}
     * @param params its parameters
     * @param collection the collection the path is in, or {@code null}
     * @return the request
     */
    private GenericSolrRequest request(String path, SolrParams params, @Nullable String collection)
    {
        GenericSolrRequest request = new GenericSolrRequest(SolrRequest.METHOD.GET, path, params);
        if (collection != null) {
            request.setRequiresCollection(true);
        }
        if (SolrSettings.JWT.equals(settings.authentication())) {
            request.addHeader("Authorization", "Bearer " + settings.token());
        }
        return request;
    }

    /**
     * Opens SolrJ's JDK HTTP client of a node: its sign-in, its trust store and its timeout.
     *
     * @param url the node's base URL
     * @param settings the settings
     * @return the client
     * @throws IOException when the trust store cannot be read
     * @throws GeneralSecurityException when the trust store cannot be used
     */
    static SolrClient open(String url, SolrSettings settings) throws IOException, GeneralSecurityException
    {
        HttpJdkSolrClient.Builder builder = new HttpJdkSolrClient.Builder(url).withConnectionTimeout(Math.min(settings.timeout(), 20_000),
                TimeUnit.MILLISECONDS).withRequestTimeout(settings.timeout(), TimeUnit.MILLISECONDS);
        if (SolrSettings.BASIC.equals(settings.authentication())) {
            builder.withBasicAuthCredentials(settings.user(), settings.password());
        }
        String trustStore = settings.trustStore();
        if (trustStore != null) {
            builder.withSSLContext(sslContext(Path.of(trustStore), settings.trustStorePassword(), settings.trustStoreType()));
        }
        return builder.build();
    }

    /**
     * Returns a TLS context that trusts the certificates of a trust store, as Java's own trust managers check them.
     *
     * @param trustStore the trust store's file
     * @param password its password, or {@code null}
     * @param type its type, such as PKCS12 or JKS
     * @return the context
     * @throws IOException when the file cannot be read
     * @throws GeneralSecurityException when it is no trust store of that type
     */
    static SSLContext sslContext(Path trustStore, @Nullable String password, String type) throws IOException, GeneralSecurityException
    {
        KeyStore store = KeyStore.getInstance(type);
        try (InputStream in = Files.newInputStream(trustStore)) {
            store.load(in, password == null ? null : password.toCharArray());
        }
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(store);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trust.getTrustManagers(), null);
        return context;
    }

    /**
     * Names why something failed.
     *
     * @param failed what was thrown
     * @return the failure to report
     */
    static LookupException failure(Throwable failed)
    {
        if (failed instanceof LookupException known) {
            return known;
        }
        return new LookupException(reason(failed), message(failed), failed);
    }

    private static LookupException.Reason reason(Throwable failed)
    {
        for (Throwable cause = failed; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof UnknownHostException || cause instanceof NoRouteToHostException
                    || cause instanceof HttpTimeoutException) {
                return LookupException.Reason.UNREACHABLE;
            }
        }
        for (Throwable cause = failed; cause != null; cause = cause.getCause()) {
            if (cause instanceof SolrException solr) {
                return status(solr.code());
            }
        }
        return LookupException.Reason.FAILED;
    }

    /** Solr answers a request without valid credentials with 401 and one the user may not make with 403. */
    private static LookupException.Reason status(int status)
    {
        return switch (status) {
            case 401 -> LookupException.Reason.AUTHENTICATION_FAILED;
            case 403 -> LookupException.Reason.ACCESS_DENIED;
            case 404 -> LookupException.Reason.NOT_FOUND;
            // A proxy or load balancer in front of the nodes answers for them when it cannot reach one.
            case 502, 503, 504 -> LookupException.Reason.UNREACHABLE;
            default -> LookupException.Reason.FAILED;
        };
    }

    /**
     * Returns what a failure says: its message, or else its cause's.
     *
     * @param failed what was thrown
     * @return the text
     */
    static String message(Throwable failed)
    {
        String message = failed.getMessage();
        Throwable cause = failed.getCause();
        if ((message == null || message.isBlank()) && cause != null) {
            return message(cause);
        }
        return message == null || message.isBlank() ? failed.getClass().getSimpleName() : message.strip().replaceAll("\\s+", " ");
    }

    @Override
    public String toString()
    {
        return "SolrNodeClient[" + settings.urls().size() + " nodes, " + settings.authentication().toUpperCase(Locale.ROOT) + "]";
    }
}
