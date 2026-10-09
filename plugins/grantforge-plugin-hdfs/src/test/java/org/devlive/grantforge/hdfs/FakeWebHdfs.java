// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.hadoop.hdfs.web.resources.StartAfterParam;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A NameNode's WebHDFS with a small file system: / holds user/, tmp/ and big/, /user holds alice/, bob/ and a file
 * notes.txt, and /big seven entries that LISTSTATUS_BATCH returns {@value #BATCH} at a time, after {@code startAfter}.
 * As a standby it refuses every call the way Hadoop does; as an old server (Hadoop 2.7) it does not know LISTSTATUS_BATCH.
 */
final class FakeWebHdfs
        implements AutoCloseable
{
    private static final Map<String, List<String[]>> TREE = Map.of(
            "/", List.of(new String[] {"user", "DIRECTORY"}, new String[] {"tmp", "DIRECTORY"}, new String[] {"big", "DIRECTORY"}),
            "/big", List.of(new String[] {"e3", "FILE"}, new String[] {"e0", "DIRECTORY"}, new String[] {"e6", "FILE"},
                    new String[] {"e1", "FILE"}, new String[] {"e5", "DIRECTORY"}, new String[] {"e2", "FILE"}, new String[] {"e4", "FILE"}),
            "/user", List.of(new String[] {"bob", "DIRECTORY"}, new String[] {"notes.txt", "FILE"}, new String[] {"alice", "DIRECTORY"}),
            "/user/alice", List.of(),
            "/user/bob", List.of(),
            "/tmp", List.of());

    final List<String> requests = new CopyOnWriteArrayList<>();

    /** Entries per LISTSTATUS_BATCH response. */
    static final int BATCH = 3;

    private final HttpServer server;
    private final boolean standby;
    private final boolean old;

    FakeWebHdfs(boolean standby) throws IOException
    {
        this(standby, false);
    }

    FakeWebHdfs(boolean standby, boolean old) throws IOException
    {
        this.standby = standby;
        this.old = old;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/webhdfs/v1", this::handle);
        server.createContext("/", exchange -> respond(exchange, 404, "<html>Not Found</html>"));
        server.start();
    }

    URI uri()
    {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private void handle(HttpExchange exchange) throws IOException
    {
        String path = URLDecoder.decode(exchange.getRequestURI().getRawPath().substring("/webhdfs/v1".length()), StandardCharsets.UTF_8);
        String query = exchange.getRequestURI().getRawQuery();
        requests.add(path + "?" + query);
        if (standby) {
            respond(exchange, 403, "{\"RemoteException\":{\"exception\":\"StandbyException\",\"javaClassName\":"
                    + "\"org.apache.hadoop.ipc.StandbyException\",\"message\":\"Operation category READ is not supported in state standby\"}}");
            return;
        }
        if (query.contains("user.name=nobody") || query.contains("user.name=stat-only") && query.contains("op=LISTSTATUS")) {
            respond(exchange, 403, "{\"RemoteException\":{\"exception\":\"AccessControlException\",\"javaClassName\":"
                    + "\"org.apache.hadoop.security.AccessControlException\",\"message\":\"Permission denied: user=nobody\"}}");
            return;
        }
        String normal = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        if (normal.isEmpty()) {
            normal = "/";
        }
        boolean directory = TREE.containsKey(normal);
        boolean file = "/user/notes.txt".equals(normal);
        if (!directory && !file) {
            respond(exchange, 404, "{\"RemoteException\":{\"exception\":\"FileNotFoundException\",\"javaClassName\":"
                    + "\"java.io.FileNotFoundException\",\"message\":\"File does not exist: " + normal + "\"}}");
            return;
        }
        if (query.contains("op=GETFILESTATUS")) {
            respond(exchange, 200, "{\"FileStatus\":" + status("", directory ? "DIRECTORY" : "FILE") + "}");
            return;
        }
        List<String[]> entries = TREE.getOrDefault(normal, List.of());
        int remaining = 0;
        if (query.contains("op=LISTSTATUS_BATCH")) {
            if (old) {
                respond(exchange, 400, "{\"RemoteException\":{\"exception\":\"IllegalArgumentException\",\"javaClassName\":"
                        + "\"java.lang.IllegalArgumentException\",\"message\":\"Invalid value for webhdfs parameter \\\"op\\\": "
                        + "No enum constant org.apache.hadoop.hdfs.web.resources.GetOpParam.Op.LISTSTATUS_BATCH\"}}");
                return;
            }
            String after = startAfter(query);
            List<String[]> sorted = entries.stream().sorted(Comparator.comparing((String[] entry) -> entry[0]))
                    .filter(entry -> entry[0].compareTo(after) > 0).toList();
            entries = sorted.subList(0, Math.min(BATCH, sorted.size()));
            remaining = sorted.size() - entries.size();
        }
        StringBuilder statuses = new StringBuilder();
        for (String[] entry : entries) {
            statuses.append(statuses.length() == 0 ? "" : ",").append(status(entry[0], entry[1]));
        }
        String listing = "{\"FileStatuses\":{\"FileStatus\":[" + statuses + "]}}";
        respond(exchange, 200, query.contains("op=LISTSTATUS_BATCH")
                ? "{\"DirectoryListing\":{\"partialListing\":" + listing + ",\"remainingEntries\":" + remaining + "}}" : listing);
    }

    private static String startAfter(String query)
    {
        for (String parameter : query.split("&", -1)) {
            if (parameter.startsWith(StartAfterParam.NAME + "=")) {
                return URLDecoder.decode(parameter.substring(StartAfterParam.NAME.length() + 1), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    /** A FileStatus with every field Hadoop's WebHDFS client reads. */
    private static String status(String name, String type)
    {
        return "{\"pathSuffix\":\"" + name + "\",\"type\":\"" + type + "\",\"length\":0,\"owner\":\"hdfs\",\"group\":\"supergroup\","
                + "\"permission\":\"755\",\"accessTime\":0,\"modificationTime\":1767225600000,\"blockSize\":134217728,\"replication\":"
                + ("FILE".equals(type) ? 3 : 0) + ",\"fileId\":16386,\"childrenNum\":0,\"storagePolicy\":0}";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException
    {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
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
