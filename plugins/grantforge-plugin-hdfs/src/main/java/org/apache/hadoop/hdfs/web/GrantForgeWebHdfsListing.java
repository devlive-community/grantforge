// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.apache.hadoop.hdfs.web;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.protocol.HdfsFileStatus;
import org.apache.hadoop.hdfs.web.resources.GetOpParam;
import org.apache.hadoop.security.authentication.client.AuthenticationException;
import org.apache.hadoop.shaded.com.fasterxml.jackson.core.JsonParser;
import org.apache.hadoop.shaded.com.fasterxml.jackson.core.JsonToken;
import org.apache.hadoop.util.JsonSerialization;
import org.jspecify.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;

/**
 * A bridge to the plugin's fixed Hadoop client, isolated from NameNode agents. Native runners retain authentication,
 * TLS, CSRF and HA handling, while success and error streams and directory entry materialization remain bounded.
 */
public final class GrantForgeWebHdfsListing
{
    private static final long MAX_RESPONSE_BYTES = 32L * 1024 * 1024;
    private static final long MAX_ERROR_BYTES = 64L * 1024;
    private static final Logger LOG = Logger.getLogger(GrantForgeWebHdfsListing.class.getName());

    private GrantForgeWebHdfsListing()
    {
    }

    /**
     * Reads one file's metadata through the native client with bounded successful and error responses.
     *
     * @param files the dedicated WebHDFS client
     * @param path the requested metadata path
     * @return its native file status
     * @throws FileNotFoundException if the response has no file status
     * @throws IOException if transport, permissions, parsing or response limits fail
     */
    public static FileStatus status(WebHdfsFileSystem files, Path path) throws IOException
    {
        URLConnectionFactory original = files.connectionFactory;
        BoundedFactory bounded = new BoundedFactory(original, MAX_ERROR_BYTES);
        files.connectionFactory = bounded;
        try {
            HttpURLConnection connection = files.new FsPathConnectionRunner(GetOpParam.Op.GETFILESTATUS, path).run();
            if (connection.getContentLength() == 0) {
                throw new FileNotFoundException("File does not exist: " + path);
            }
            try (InputStream input = connection.getInputStream()) {
                Map<?, ?> json = JsonSerialization.mapReader().readValue(input);
                if (json == null || json.get("FileStatus") == null) {
                    throw new FileNotFoundException("File does not exist: " + path);
                }
                HdfsFileStatus status;
                try {
                    status = JsonUtilClient.toFileStatus(json, true);
                }
                catch (RuntimeException invalid) {
                    throw new IOException("invalid WebHDFS file metadata", invalid);
                }
                if (status == null) {
                    throw new FileNotFoundException("File does not exist: " + path);
                }
                return status.makeQualified(files.getUri(), path);
            }
        }
        finally {
            files.connectionFactory = original;
            bounded.closeAll();
        }
    }

    /**
     * Uses LISTSTATUS, supported by old and current WebHDFS servers; network or permission failures never become a fallback.
     *
     * @param files the plugin's dedicated, authenticated WebHDFS client
     * @param path the directory
     * @param maximum the scan limit, independent of the requested number of lookup suggestions
     * @return entries from the requested directory
     * @throws IOException if transport, response bounds, parsing or permissions fail
     * @throws IllegalArgumentException if the maximum is outside one through 100000
     */
    public static List<FileStatus> list(WebHdfsFileSystem files, Path path, int maximum) throws IOException
    {
        if (maximum < 1 || maximum > 100000) {
            throw new IllegalArgumentException("directory scan limit must be between 1 and 100000");
        }
        URLConnectionFactory original = files.connectionFactory;
        long limit = Math.min(MAX_RESPONSE_BYTES, MAX_ERROR_BYTES + maximum * 4096L);
        BoundedFactory bounded = new BoundedFactory(original, limit);
        files.connectionFactory = bounded;
        try {
            HttpURLConnection connection = files.new FsPathConnectionRunner(GetOpParam.Op.LISTSTATUS, path).run();
            try (InputStream input = connection.getInputStream(); JsonParser parser = JsonSerialization.mapReader().createParser(input)) {
                return parse(parser, files, path, maximum);
            }
        }
        finally {
            files.connectionFactory = original;
            bounded.closeAll();
        }
    }

    private static List<FileStatus> parse(JsonParser parser, WebHdfsFileSystem files, Path path, int maximum) throws IOException
    {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new IOException("invalid WebHDFS LISTSTATUS response");
        }
        @Nullable List<FileStatus> entries = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) {
                throw new IOException("invalid WebHDFS LISTSTATUS response");
            }
            String field = parser.currentName();
            parser.nextToken();
            if ("FileStatuses".equals(field)) {
                if (entries != null || parser.currentToken() != JsonToken.START_OBJECT) {
                    throw new IOException("invalid WebHDFS FileStatuses response");
                }
                entries = entries(parser, files, path, maximum);
            }
            else {
                parser.skipChildren();
            }
        }
        if (entries == null || parser.nextToken() != null) {
            throw new IOException("missing or trailing WebHDFS LISTSTATUS response");
        }
        return entries;
    }

    private static List<FileStatus> entries(JsonParser parser, WebHdfsFileSystem files, Path path, int maximum) throws IOException
    {
        @Nullable List<FileStatus> entries = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) {
                throw new IOException("invalid WebHDFS FileStatuses response");
            }
            String field = parser.currentName();
            parser.nextToken();
            if ("FileStatus".equals(field)) {
                if (entries != null || parser.currentToken() != JsonToken.START_ARRAY) {
                    throw new IOException("invalid WebHDFS FileStatus array");
                }
                entries = array(parser, files, path, maximum);
            }
            else {
                parser.skipChildren();
            }
        }
        if (entries == null) {
            throw new IOException("missing WebHDFS FileStatus array");
        }
        return entries;
    }

    private static List<FileStatus> array(JsonParser parser, WebHdfsFileSystem files, Path path, int maximum) throws IOException
    {
        List<FileStatus> entries = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                throw new IOException("invalid WebHDFS directory entry");
            }
            if (entries.size() >= maximum) {
                throw new IOException("directory exceeds " + maximum + " entries; narrow the lookup directory using lookup.path");
            }
            Map<?, ?> status = JsonSerialization.mapReader().readValue(parser);
            try {
                entries.add(requireNonNull(JsonUtilClient.toFileStatus(status, false), "directory entry").makeQualified(files.getUri(), path));
            }
            catch (RuntimeException invalid) {
                throw new IOException("invalid WebHDFS directory entry", invalid);
            }
        }
        return entries;
    }

    private static final class BoundedFactory
            extends URLConnectionFactory
    {
        private final URLConnectionFactory delegate;
        private final long limit;
        private final List<HttpURLConnection> opened = new CopyOnWriteArrayList<>();

        BoundedFactory(URLConnectionFactory delegate, long limit)
        {
            super(connection -> connection);
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public URLConnection openConnection(URL url) throws IOException
        {
            return wrap(delegate.openConnection(url));
        }

        @Override
        public URLConnection openConnection(URL url, boolean spnego) throws IOException, AuthenticationException
        {
            return wrap(delegate.openConnection(url, spnego));
        }

        private URLConnection wrap(URLConnection connection) throws IOException
        {
            if (!(connection instanceof HttpURLConnection http)) {
                throw new IOException("WebHDFS returned a non-HTTP connection");
            }
            opened.add(http);
            return new BoundedConnection(http, limit);
        }

        private void closeAll()
        {
            for (HttpURLConnection connection : opened) {
                try {
                    connection.disconnect();
                }
                catch (RuntimeException unavailable) {
                    // Cleanup must not hide the native denial, response-bound or parsing exception being propagated.
                    LOG.log(Level.FINE, "Could not disconnect a completed WebHDFS metadata connection", unavailable);
                }
            }
        }
    }

    private static final class BoundedConnection
            extends HttpURLConnection
    {
        private final HttpURLConnection delegate;
        private final long limit;

        BoundedConnection(HttpURLConnection delegate, long limit)
        {
            super(delegate.getURL());
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public InputStream getInputStream() throws IOException
        {
            requireJsonContentType();
            if (delegate.getContentLengthLong() > limit) {
                throw new IOException("WebHDFS directory response exceeds " + limit + " bytes");
            }
            return new BoundedStream(delegate.getInputStream(), limit);
        }

        @Override
        public @Nullable InputStream getErrorStream()
        {
            try {
                requireJsonContentType();
            }
            catch (IOException invalid) {
                throw new UncheckedIOException(invalid);
            }
            InputStream error = delegate.getErrorStream();
            return error == null ? null : new BoundedStream(error, MAX_ERROR_BYTES);
        }

        @Override
        public void connect() throws IOException
        {
            delegate.connect();
        }
        @Override
        public void disconnect()
        {
            delegate.disconnect();
        }
        @Override
        public boolean usingProxy()
        {
            return delegate.usingProxy();
        }
        @Override
        public void setRequestMethod(String method) throws java.net.ProtocolException
        {
            delegate.setRequestMethod(method);
        }
        @Override
        public void setDoOutput(boolean value)
        {
            delegate.setDoOutput(value);
        }
        @Override
        public void setInstanceFollowRedirects(boolean value)
        {
            delegate.setInstanceFollowRedirects(value);
        }
        @Override
        public void setRequestProperty(String name, String value)
        {
            delegate.setRequestProperty(name, value);
        }
        @Override
        public OutputStream getOutputStream() throws IOException
        {
            return delegate.getOutputStream();
        }
        @Override
        public int getResponseCode() throws IOException
        {
            return delegate.getResponseCode();
        }
        @Override
        public @Nullable String getResponseMessage() throws IOException
        {
            return delegate.getResponseMessage();
        }
        @Override
        public @Nullable String getHeaderField(String name)
        {
            return delegate.getHeaderField(name);
        }
        @Override
        public int getContentLength()
        {
            return delegate.getContentLength();
        }
        @Override
        public long getContentLengthLong()
        {
            return delegate.getContentLengthLong();
        }
        @Override
        public @Nullable String getContentType()
        {
            // Hadoop 3.5's shaded client lacks the Jersey RuntimeDelegate used by MediaType.valueOf.
            // Validate JSON here, then let the native runner decode without invoking that missing provider.
            try {
                requireJsonContentType();
            }
            catch (IOException invalid) {
                throw new UncheckedIOException(invalid);
            }
            return null;
        }

        private void requireJsonContentType() throws IOException
        {
            String type = delegate.getContentType();
            if (type != null && !"application/json".equalsIgnoreCase(type.split(";", 2)[0].trim())) {
                throw new IOException("WebHDFS Content-Type is incompatible with application/json: " + type);
            }
        }
    }

    private static final class BoundedStream
            extends FilterInputStream
    {
        private final long limit;
        private long count;

        BoundedStream(InputStream input, long limit)
        {
            super(input);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException
        {
            int value = in.read();
            if (value >= 0) {
                consumed(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException
        {
            int read = in.read(bytes, offset, (int) Math.min(length, Math.max(1, limit - count + 1)));
            if (read > 0) {
                consumed(read);
            }
            return read;
        }

        private void consumed(long read) throws IOException
        {
            count += read;
            if (count > limit) {
                throw new IOException("WebHDFS response exceeds " + limit + " bytes");
            }
        }
    }
}
