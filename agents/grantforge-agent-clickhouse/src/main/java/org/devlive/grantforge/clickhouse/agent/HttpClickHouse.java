// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.ConnectionInitiationException;
import com.clickhouse.client.api.ServerException;
import com.clickhouse.client.api.query.GenericRecord;
import com.clickhouse.client.api.query.QuerySettings;
import com.clickhouse.data.ClickHouseColumn;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLException;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * ClickHouse through its own Java client over HTTP: one client per server, kept open, and the next server when one
 * cannot be reached. Thread-safe, as the clients are. A server that answers decides, refusals included. Statements longer than ClickHouse parses by
 * default, such as a role's grants on many tables, raise its limit for themselves.
 */
final class HttpClickHouse
        implements ClickHouse
{
    /** What ClickHouse parses of a statement unless told otherwise: 256 KiB. */
    static final int DEFAULT_QUERY_SIZE = 262_144;

    private final List<String> urls;
    private final List<Client> clients;
    private final long timeout;
    private final AtomicInteger current = new AtomicInteger();

    HttpClickHouse(AgentConfig.Connection connection)
    {
        this(connection, url -> client(connection, url));
    }

    HttpClickHouse(AgentConfig.Connection connection, Function<String, Client> factory)
    {
        this.urls = connection.urls();
        this.timeout = connection.timeout().toMillis();
        List<Client> built = new ArrayList<>();
        for (String url : urls) {
            built.add(factory.apply(url));
        }
        this.clients = built;
    }

    /**
     * Builds the client of one server.
     *
     * @param connection the servers and the sign-in
     * @param url the server
     * @return the client
     */
    static Client client(AgentConfig.Connection connection, String url)
    {
        long timeout = connection.timeout().toMillis();
        Client.Builder builder = new Client.Builder().addEndpoint(url).setUsername(connection.user())
                .setConnectTimeout(timeout, ChronoUnit.MILLIS).setSocketTimeout(timeout, ChronoUnit.MILLIS)
                .setExecutionTimeout(timeout, ChronoUnit.MILLIS).setMaxRetries(0).setClientName("grantforge-agent");
        if (connection.certificate() != null) {
            builder.useSSLAuthentication(true).setClientCertificate(connection.certificate()).setClientKey(String.valueOf(connection.key()));
        }
        else {
            builder.setPassword(connection.password() == null ? "" : connection.password());
        }
        if (connection.ca() != null) {
            builder.setRootCertificate(connection.ca());
        }
        return builder.build();
    }

    @Override
    public List<Row> query(String sql, Map<String, Object> parameters)
    {
        return attempt(client -> {
            QuerySettings settings = new QuerySettings();
            settings.setNetworkTimeout(timeout, ChronoUnit.MILLIS);
            List<Row> rows = new ArrayList<>();
            for (GenericRecord record : client.queryAll(sql, parameters, settings)) {
                rows.add(row(record));
            }
            return rows;
        });
    }

    /** A record as a row: arrays as lists, through the client's own reading of them, and NULL as no value. */
    static Row row(GenericRecord record)
    {
        Map<String, @Nullable Object> values = new HashMap<>();
        for (ClickHouseColumn column : record.getSchema().getColumns()) {
            String name = column.getColumnName();
            values.put(name, !record.hasValue(name) ? null : column.isArray() ? record.getList(name) : record.getObject(name));
        }
        return new Row(values);
    }

    @Override
    public void execute(String sql)
    {
        attempt(client -> {
            QuerySettings settings = new QuerySettings();
            settings.setNetworkTimeout(timeout, ChronoUnit.MILLIS);
            if (sql.length() > DEFAULT_QUERY_SIZE / 2) {
                settings.serverSetting("max_query_size", Integer.toString(sql.length() * 4 + DEFAULT_QUERY_SIZE));
            }
            return client.queryAll(sql, Map.of(), settings);
        });
    }

    /** Asks the server that answered last, then the others in turn, until one answers. */
    private <T> T attempt(Function<Client, T> request)
    {
        ClickHouseFailure unreachable = null;
        for (int tried = 0; tried < clients.size(); tried++) {
            int index = (current.get() + tried) % clients.size();
            try {
                T result = request.apply(clients.get(index));
                current.set(index);
                return result;
            }
            catch (RuntimeException failed) {
                ClickHouseFailure reason = failure(urls.get(index), failed);
                if (reason.code() != ClickHouseFailure.UNREACHABLE) {
                    current.set(index);
                    throw reason;
                }
                unreachable = unreachable == null ? reason : unreachable;
            }
        }
        throw unreachable == null ? new ClickHouseFailure(ClickHouseFailure.UNREACHABLE, "no ClickHouse server is configured", null) : unreachable;
    }

    /**
     * Names why a request failed: ClickHouse's code when a server answered, {@link ClickHouseFailure#UNREACHABLE} when
     * none did.
     *
     * @param url the server asked
     * @param failed what the client threw
     * @return the failure
     */
    static ClickHouseFailure failure(String url, Throwable failed)
    {
        String text = url + ": " + message(failed);
        for (Throwable cause = failed; cause != null; cause = cause.getCause()) {
            if (cause instanceof ServerException server) {
                // Something answered over HTTP without being ClickHouse, such as a proxy in front of a server that is down.
                boolean stranger = server.getCode() == ServerException.CODE_UNKNOWN && server.getTransportProtocolCode() >= 500;
                return new ClickHouseFailure(stranger ? ClickHouseFailure.UNREACHABLE : server.getCode(), text, failed);
            }
            if (unreachable(cause)) {
                return new ClickHouseFailure(ClickHouseFailure.UNREACHABLE, text, failed);
            }
        }
        return new ClickHouseFailure(0, text, failed);
    }

    private static boolean unreachable(Throwable cause)
    {
        // A server whose certificate cannot be verified is as good as unreachable: nothing may be asked of it.
        return cause instanceof ConnectionInitiationException || cause instanceof ConnectException || cause instanceof UnknownHostException
                || cause instanceof NoRouteToHostException || cause instanceof SocketTimeoutException || cause instanceof SSLException;
    }

    /** The first line of a failure, which is the useful part of the client's message. */
    static String message(Throwable failure)
    {
        String text = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        int line = text.indexOf('\n');
        return (line < 0 ? text : text.substring(0, line)).strip();
    }

    // The clients are this object's own; closing it closes them.
    @SuppressWarnings("PMD.CloseResource")
    @Override
    public void close()
    {
        for (Client client : clients) {
            client.close();
        }
    }

    /** For the tests: the server the next request goes to first. */
    @Nullable String preferred()
    {
        return clients.isEmpty() ? null : urls.get(current.get());
    }
}
