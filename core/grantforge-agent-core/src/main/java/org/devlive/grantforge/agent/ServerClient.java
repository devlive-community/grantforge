// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls the agent API of the server ({@code /api/v1/agent/**}) with the service's token. Thread-safe; every call opens
 * its own connection, which {@link HttpURLConnection} keeps alive between calls.
 */
final class ServerClient
{
    static final String SIGNATURE_HEADER = "X-GrantForge-Signature";
    static final String KEY_HEADER = "X-GrantForge-Signing-Key";
    static final String VERSION_HEADER = "X-GrantForge-Policy-Version";

    /** Largest answer read; a snapshot of a large service is a few megabytes. */
    static final int MAX_BODY = 64 * 1024 * 1024;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AgentSettings settings;
    private final URI base;

    ServerClient(AgentSettings settings)
    {
        this.settings = settings;
        String address = settings.server().toString();
        this.base = URI.create(address.endsWith("/") ? address : address + "/");
    }

    /**
     * Reports that the agent is alive and learns the current policy version.
     *
     * @param appliedPolicyVersion the version the agent applies, or {@code null} before its first snapshot
     * @return the server's answer
     * @throws IOException if the server cannot be reached or refuses
     */
    Heartbeat heartbeat(@Nullable Long appliedPolicyVersion) throws IOException
    {
        Map<String, @Nullable Object> body = new LinkedHashMap<>();
        body.put("instance", settings.instance());
        body.put("host", settings.host());
        body.put("agentVersion", settings.agentVersion());
        body.put("appliedPolicyVersion", appliedPolicyVersion);
        JsonNode answer = json(send("POST", "api/v1/agent/heartbeat", JSON.writeValueAsBytes(body), null).body);
        return new Heartbeat(answer.path("policyVersion").asLong(), answer.path("refreshSeconds").asLong());
    }

    /**
     * Downloads the service's policy snapshot, unless the agent has it already.
     *
     * @param etag the ETag of the snapshot the agent has, or {@code null}
     * @return the snapshot, or that it has not changed
     * @throws IOException if the server cannot be reached or refuses
     */
    Download policies(@Nullable String etag) throws IOException
    {
        Response response = send("GET", "api/v1/agent/policies", null, etag);
        if (response.status == HttpURLConnection.HTTP_NOT_MODIFIED) {
            return Download.unchanged();
        }
        String signature = response.header(SIGNATURE_HEADER);
        String keyId = response.header(KEY_HEADER);
        String tag = response.header("ETag");
        if (signature == null || keyId == null || tag == null) {
            throw new IOException("the snapshot came without its ETag, signature or key id");
        }
        return new Download(response.body, tag, keyId, signature);
    }

    /**
     * Fetches the public key snapshots are signed with.
     *
     * @return the key
     * @throws IOException if the server cannot be reached or refuses
     * @throws IllegalArgumentException if the answer is not an Ed25519 key
     */
    SigningKey signingKey() throws IOException
    {
        JsonNode answer = json(send("GET", "api/v1/agent/signing-key", null, null).body);
        return SigningKey.of(answer.path("publicKey").asText(""));
    }

    /**
     * Sends a batch of access events.
     *
     * @param events the events, as {@link AccessEvent#fields()} gives them
     * @return how many the server stored
     * @throws IOException if the server cannot be reached or refuses
     */
    int send(List<Map<String, @Nullable Object>> events) throws IOException
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("instance", settings.instance());
        body.put("events", events);
        JsonNode answer = json(send("POST", "api/v1/agent/access-events", JSON.writeValueAsBytes(body), null).body);
        return answer.path("accepted").asInt() + answer.path("duplicates").asInt();
    }

    private Response send(String method, String path, byte @Nullable [] body, @Nullable String etag) throws IOException
    {
        URL url = base.resolve(path).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout((int) settings.connectTimeout().toMillis());
            connection.setReadTimeout((int) settings.readTimeout().toMillis());
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Authorization", "Bearer " + settings.token());
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "grantforge-agent/" + settings.agentVersion());
            if (etag != null) {
                connection.setRequestProperty("If-None-Match", etag);
            }
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body);
                }
            }
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
                return new Response(status, new byte[0], connection);
            }
            if (status / 100 != 2) {
                throw new IOException(method + " " + path + " answered " + status + ": " + text(read(connection.getErrorStream())));
            }
            return new Response(status, read(connection.getInputStream()), connection);
        }
        finally {
            connection.disconnect();
        }
    }

    private static byte[] read(@Nullable InputStream stream) throws IOException
    {
        if (stream == null) {
            return new byte[0];
        }
        try (InputStream in = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read = in.read(buffer);
            while (read != -1) {
                if (out.size() + read > MAX_BODY) {
                    throw new IOException("the answer is larger than " + MAX_BODY + " bytes");
                }
                out.write(buffer, 0, read);
                read = in.read(buffer);
            }
            return out.toByteArray();
        }
    }

    private static String text(byte[] body)
    {
        String text = new String(body, StandardCharsets.UTF_8);
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    private static JsonNode json(byte[] body) throws IOException
    {
        JsonNode node = JSON.readTree(body);
        if (node == null || !node.isObject()) {
            throw new IOException("the server's answer is not a JSON object");
        }
        return node;
    }

    /** An answer, read before the connection is let go; it owns the body it was read into. */
    @SuppressWarnings("PMD.ArrayIsStoredDirectly")
    private static final class Response
    {
        final int status;
        final byte[] body;
        private final Map<String, String> headers = new LinkedHashMap<>();

        Response(int status, byte[] body, HttpURLConnection connection)
        {
            this.status = status;
            this.body = body;
            for (String name : new String[] {SIGNATURE_HEADER, KEY_HEADER, VERSION_HEADER, "ETag"}) {
                String value = connection.getHeaderField(name);
                if (value != null) {
                    headers.put(name, value);
                }
            }
        }

        @Nullable String header(String name)
        {
            return headers.get(name);
        }
    }

    /** The answer to a heartbeat. */
    static final class Heartbeat
    {
        private final long policyVersion;
        private final long refreshSeconds;

        Heartbeat(long policyVersion, long refreshSeconds)
        {
            this.policyVersion = policyVersion;
            this.refreshSeconds = refreshSeconds;
        }

        long policyVersion()
        {
            return policyVersion;
        }

        long refreshSeconds()
        {
            return refreshSeconds;
        }
    }

    /** A downloaded snapshot, or that the agent's is current. Holds the body as it is: snapshots run to megabytes. */
    @SuppressWarnings({"PMD.ArrayIsStoredDirectly", "PMD.MethodReturnsInternalArray"})
    static final class Download
    {
        private static final Download UNCHANGED = new Download(new byte[0], "", "", "");

        private final byte[] body;
        private final String etag;
        private final String keyId;
        private final String signature;

        Download(byte[] body, String etag, String keyId, String signature)
        {
            this.body = body;
            this.etag = etag;
            this.keyId = keyId;
            this.signature = signature;
        }

        static Download unchanged()
        {
            return UNCHANGED;
        }

        boolean changed()
        {
            return this != UNCHANGED;
        }

        byte[] body()
        {
            return body;
        }

        String etag()
        {
            return etag;
        }

        String keyId()
        {
            return keyId;
        }

        String signature()
        {
            return signature;
        }
    }
}
