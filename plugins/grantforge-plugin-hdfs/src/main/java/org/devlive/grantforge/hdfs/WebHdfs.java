// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static java.util.Objects.requireNonNull;

/**
 * The few WebHDFS (or HttpFS) calls the service type needs, over the JDK's HTTP client with simple authentication
 * ({@code user.name}). With several NameNode addresses, as in a high-availability pair, the calls go to the first one
 * that answers as active: a standby NameNode refuses with a {@code StandbyException} and the next is asked.
 */
final class WebHdfs
{
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PREFIX = "/webhdfs/v1";

    private final List<URI> addresses;
    private final String user;
    private final Duration timeout;
    private final HttpClient http;

    /**
     * Creates the client.
     *
     * @param addresses the NameNode or HttpFS addresses, such as {@code http://namenode:9870}
     * @param user the user to act as
     * @param timeout how long a call may take
     */
    WebHdfs(List<URI> addresses, String user, Duration timeout)
    {
        if (addresses.isEmpty()) {
            throw new IllegalArgumentException("no WebHDFS address");
        }
        this.addresses = List.copyOf(addresses);
        this.user = requireNonNull(user, "user");
        this.timeout = requireNonNull(timeout, "timeout");
        this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /**
     * Parses a comma-separated list of addresses.
     *
     * @param text the addresses
     * @return the addresses, without their trailing slashes
     * @throws IllegalArgumentException if one is not an http or https address
     */
    static List<URI> addresses(String text)
    {
        List<URI> addresses = new ArrayList<>();
        for (String part : text.split(",")) {
            String address = part.strip();
            if (address.isEmpty()) {
                continue;
            }
            while (address.endsWith("/")) {
                address = address.substring(0, address.length() - 1);
            }
            URI uri;
            try {
                uri = URI.create(address);
            }
            catch (IllegalArgumentException broken) {
                throw new IllegalArgumentException(part.strip() + " is not an address", broken);
            }
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null) {
                throw new IllegalArgumentException(part.strip() + " is not an http or https address");
            }
            addresses.add(uri);
        }
        if (addresses.isEmpty()) {
            throw new IllegalArgumentException("no address given");
        }
        return addresses;
    }

    /**
     * Returns the status of a path.
     *
     * @param path an absolute path
     * @return the status, or {@code null} if the path does not exist
     * @throws IOException if no NameNode answers, or one refuses
     */
    @Nullable Entry status(String path) throws IOException
    {
        JsonNode answer = call(path, "GETFILESTATUS");
        return answer == null ? null : entry(answer.path("FileStatus"));
    }

    /**
     * Lists a directory.
     *
     * @param path an absolute path
     * @return its entries, or an empty list if it does not exist
     * @throws IOException if no NameNode answers, or one refuses
     */
    List<Entry> list(String path) throws IOException
    {
        JsonNode answer = call(path, "LISTSTATUS");
        if (answer == null) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        for (JsonNode status : answer.path("FileStatuses").path("FileStatus")) {
            entries.add(entry(status));
        }
        return entries;
    }

    private static Entry entry(JsonNode status)
    {
        return new Entry(status.path("pathSuffix").asString(""), "DIRECTORY".equals(status.path("type").asString("")));
    }

    /** Calls the first NameNode that answers as active; {@code null} when the path does not exist. */
    private @Nullable JsonNode call(String path, String operation) throws IOException
    {
        IOException last = null;
        for (URI address : addresses) {
            try {
                return call(address, path, operation);
            }
            catch (StandbyException standby) {
                last = standby;
            }
            catch (Refused refused) {
                throw refused;
            }
            catch (IOException unreachable) {
                last = unreachable;
            }
        }
        throw requireNonNull(last, "no address was asked");
    }

    // Restores the caller's interrupt when the call is interrupted.
    @SuppressWarnings("PMD.DoNotUseThreads")
    private @Nullable JsonNode call(URI address, String path, String operation) throws IOException
    {
        URI uri = URI.create(address + PREFIX + encode(path) + "?op=" + operation + "&user.name="
                + URLEncoder.encode(user, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).header("Accept", "application/json").GET().build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while asking " + address, interrupted);
        }
        catch (IOException unreachable) {
            throw new IOException(address + " cannot be reached: " + unreachable.getMessage(), unreachable);
        }
        JsonNode body = json(response.body());
        if (response.statusCode() == 200) {
            if (body == null) {
                throw new IOException(address + " did not answer in JSON; is it a WebHDFS address?");
            }
            return body;
        }
        JsonNode remote = body == null ? null : body.path("RemoteException");
        String exception = remote == null ? "" : remote.path("exception").asString("");
        String message = remote == null ? "" : remote.path("message").asString("");
        if ("StandbyException".equals(exception)) {
            throw new StandbyException(address + " is a standby NameNode");
        }
        if (response.statusCode() == 404 && ("FileNotFoundException".equals(exception) || exception.isEmpty())) {
            if (remote == null || remote.isMissingNode()) {
                throw new Refused(address + " answered 404; is it a WebHDFS address?");
            }
            return null;
        }
        throw new Refused(address + " refused " + operation.toLowerCase(Locale.ROOT) + " " + path + " (" + response.statusCode()
                + (exception.isEmpty() ? "" : " " + exception) + (message.isEmpty() ? "" : ": " + message) + ")");
    }

    private static @Nullable JsonNode json(String body)
    {
        try {
            JsonNode node = JSON.readTree(body);
            return node != null && node.isObject() ? node : null;
        }
        catch (JacksonException notJson) {
            return null;
        }
    }

    /** Encodes each segment of a path for the URL, keeping the slashes. */
    static String encode(String path)
    {
        StringBuilder encoded = new StringBuilder();
        for (String segment : path.split("/", -1)) {
            if (encoded.length() > 0 || !segment.isEmpty()) {
                encoded.append('/');
            }
            encoded.append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        String result = encoded.toString();
        return result.startsWith("/") ? result : "/" + result;
    }

    /**
     * A directory entry.
     *
     * @param name its name within the directory
     * @param directory whether it is a directory
     */
    record Entry(String name, boolean directory)
    {
    }

    /** A NameNode that is standby, so the next one is asked. */
    static final class StandbyException
            extends IOException
    {
        private static final long serialVersionUID = 1L;

        StandbyException(String message)
        {
            super(message);
        }
    }

    /** A NameNode that answered and refused, so asking another does not help. */
    static final class Refused
            extends IOException
    {
        private static final long serialVersionUID = 1L;

        Refused(String message)
        {
            super(message);
        }
    }
}
