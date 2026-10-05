// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A NameNode's WebHDFS with a small file system: / holds user/ and tmp/, /user holds alice/, bob/ and a file notes.txt.
 * As a standby it refuses every call the way Hadoop does.
 */
final class FakeWebHdfs
        implements AutoCloseable
{
    private static final Map<String, List<String[]>> TREE = Map.of(
            "/", List.of(new String[] {"user", "DIRECTORY"}, new String[] {"tmp", "DIRECTORY"}),
            "/user", List.of(new String[] {"bob", "DIRECTORY"}, new String[] {"notes.txt", "FILE"}, new String[] {"alice", "DIRECTORY"}),
            "/user/alice", List.of(),
            "/user/bob", List.of(),
            "/tmp", List.of());

    final List<String> requests = new CopyOnWriteArrayList<>();

    private final HttpServer server;
    private final boolean standby;

    FakeWebHdfs(boolean standby) throws IOException
    {
        this.standby = standby;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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
        if (query.contains("user.name=nobody")) {
            respond(exchange, 403, "{\"RemoteException\":{\"exception\":\"AccessControlException\",\"message\":\"Permission denied: user=nobody\"}}");
            return;
        }
        String normal = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        if (normal.isEmpty()) {
            normal = "/";
        }
        boolean directory = TREE.containsKey(normal);
        boolean file = "/user/notes.txt".equals(normal);
        if (!directory && !file) {
            respond(exchange, 404, "{\"RemoteException\":{\"exception\":\"FileNotFoundException\",\"message\":\"File does not exist: "
                    + normal + "\"}}");
            return;
        }
        if (query.contains("op=GETFILESTATUS")) {
            respond(exchange, 200, "{\"FileStatus\":{\"pathSuffix\":\"\",\"type\":\"" + (directory ? "DIRECTORY" : "FILE") + "\"}}");
            return;
        }
        StringBuilder statuses = new StringBuilder();
        for (String[] entry : TREE.getOrDefault(normal, List.of())) {
            statuses.append(statuses.length() == 0 ? "" : ",").append("{\"pathSuffix\":\"").append(entry[0]).append("\",\"type\":\"")
                    .append(entry[1]).append("\"}");
        }
        respond(exchange, 200, "{\"FileStatuses\":{\"FileStatus\":[" + statuses + "]}}");
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
