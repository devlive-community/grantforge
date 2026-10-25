// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.ClientException;
import com.clickhouse.client.api.ConnectionInitiationException;
import com.clickhouse.client.api.ServerException;
import com.clickhouse.client.api.query.GenericRecord;
import com.clickhouse.client.api.query.QuerySettings;
import org.devlive.grantforge.plugin.api.LookupException;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLException;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Asks the servers of a service through ClickHouse's own Java client over HTTP: the first server, or the next when one
 * cannot be reached. A server that answers decides, refusals included.
 */
final class ClickHouseNodeClient
{
    /**
     * ClickHouse's codes for a sign-in it refused: authentication failed, an unknown user, a wrong or missing password, an
     * address the user may not come from.
     */
    private static final List<Integer> SIGN_IN_REFUSED = List.of(516, 192, 193, 194, 195);
    /** ClickHouse's codes for a request the user may not make: access denied, a read-only user. */
    private static final List<Integer> NOT_ALLOWED = List.of(497, 164);

    private final ClickHouseSettings settings;
    private final Function<String, Client> clients;

    ClickHouseNodeClient(ClickHouseSettings settings)
    {
        this(settings, url -> client(settings, url));
    }

    ClickHouseNodeClient(ClickHouseSettings settings, Function<String, Client> clients)
    {
        this.settings = settings;
        this.clients = clients;
    }

    /**
     * Builds the client of one server.
     *
     * @param settings the service's settings
     * @param url the server
     * @return the client, which the caller closes
     */
    static Client client(ClickHouseSettings settings, String url)
    {
        Client.Builder builder = new Client.Builder().addEndpoint(url).setUsername(settings.user())
                .setConnectTimeout(settings.timeout(), ChronoUnit.MILLIS).setSocketTimeout(settings.timeout(), ChronoUnit.MILLIS)
                .setExecutionTimeout(settings.timeout(), ChronoUnit.MILLIS).setMaxRetries(0).compressServerResponse(false)
                .setClientName("grantforge");
        if (ClickHouseSettings.CERTIFICATE_SIGN_IN.equals(settings.authentication())) {
            builder.useSSLAuthentication(true).setClientCertificate(String.valueOf(settings.clientCertificate()))
                    .setClientKey(String.valueOf(settings.clientKey()));
        }
        else {
            builder.setPassword(settings.password() == null ? "" : settings.password());
        }
        if (settings.rootCertificate() != null) {
            builder.setRootCertificate(settings.rootCertificate());
        }
        return builder.build();
    }

    /**
     * Runs a query against the first server that can be reached.
     *
     * @param sql the query, with {@code {name:Type}} parameters
     * @param parameters the parameters' values
     * @return the rows
     * @throws LookupException when no server can be reached, or one refuses
     */
    List<GenericRecord> query(String sql, Map<String, Object> parameters)
    {
        LookupException unreachable = null;
        QuerySettings query = new QuerySettings();
        query.setNetworkTimeout(settings.timeout(), ChronoUnit.MILLIS);
        for (String url : settings.urls()) {
            try (Client client = clients.apply(url)) {
                return client.queryAll(sql, parameters, query);
            }
            catch (RuntimeException failed) {
                LookupException reason = failure(url, failed);
                if (reason.getReason() != LookupException.Reason.UNREACHABLE) {
                    throw reason;
                }
                if (unreachable == null) {
                    unreachable = reason;
                }
            }
        }
        throw unreachable == null ? new LookupException(LookupException.Reason.UNREACHABLE, "no ClickHouse server is configured") : unreachable;
    }

    /**
     * Names why a request failed, from the first failure in the chain that tells.
     *
     * @param url the server asked
     * @param failed what the client threw
     * @return the failure
     */
    static LookupException failure(String url, Throwable failed)
    {
        String text = url + ": " + message(failed);
        for (Throwable cause = failed; cause != null; cause = cause.getCause()) {
            LookupException.@Nullable Reason reason = reason(cause);
            if (reason != null) {
                return new LookupException(reason, text, failed);
            }
        }
        return new LookupException(LookupException.Reason.FAILED, text, failed);
    }

    private static LookupException.@Nullable Reason reason(Throwable cause)
    {
        if (cause instanceof ServerException server) {
            if (SIGN_IN_REFUSED.contains(server.getCode())) {
                return LookupException.Reason.AUTHENTICATION_FAILED;
            }
            if (NOT_ALLOWED.contains(server.getCode())) {
                return LookupException.Reason.ACCESS_DENIED;
            }
            // Something answered over HTTP without being ClickHouse, such as a proxy in front of a server that is down.
            if (server.getCode() == ServerException.CODE_UNKNOWN && server.getTransportProtocolCode() >= 500) {
                return LookupException.Reason.UNREACHABLE;
            }
            return LookupException.Reason.FAILED;
        }
        // A server whose certificate cannot be verified is as good as unreachable: nothing may be asked of it.
        if (cause instanceof ConnectionInitiationException || cause instanceof ConnectException || cause instanceof UnknownHostException
                || cause instanceof NoRouteToHostException || cause instanceof SocketTimeoutException || cause instanceof SSLException
                || cause instanceof TimeoutException) {
            return LookupException.Reason.UNREACHABLE;
        }
        if (cause instanceof ClientException && cause.getMessage() != null && cause.getMessage().contains("timed out")) {
            return LookupException.Reason.UNREACHABLE;
        }
        return null;
    }

    /** The first line of a failure, which is the useful part of the client's message. */
    static String message(Throwable failure)
    {
        String text = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        int line = text.indexOf('\n');
        return (line < 0 ? text : text.substring(0, line)).strip();
    }

    /**
     * Reads one text column of rows.
     *
     * @param rows the rows
     * @param column the column's name
     * @return the values
     */
    static List<String> texts(List<GenericRecord> rows, String column)
    {
        List<String> values = new ArrayList<>(rows.size());
        for (GenericRecord row : rows) {
            values.add(row.getString(column));
        }
        return values;
    }
}
