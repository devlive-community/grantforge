// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.apache.hadoop.hdfs.web;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrantForgeWebHdfsListingTest
{
    @Test
    void rejectsInvalidScanBoundsBeforeOpeningAnyAuthenticatedConnection()
    {
        WebHdfsFileSystem files = new WebHdfsFileSystem();
        assertThatThrownBy(() -> GrantForgeWebHdfsListing.list(files, new Path("/"), 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 and 100000");
        assertThatThrownBy(() -> GrantForgeWebHdfsListing.list(files, new Path("/"), 100001))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 and 100000");
    }

    @Test
    void disconnectsOversizeAndWrongMediaMetadataAndRestoresTheNativeFactory() throws IOException
    {
        for (String type : new String[] {"application/json", "text/html"}) {
            TrackingConnection connection = new TrackingConnection(200, "{}", type, 100000);
            try (WebHdfsFileSystem files = filesystem(connection)) {
                URLConnectionFactory original = files.connectionFactory;
                assertThatThrownBy(() -> GrantForgeWebHdfsListing.status(files, new Path("/data")))
                        .isInstanceOf(IOException.class);
                assertThat(connection.disconnected).isTrue();
                assertThat(files.connectionFactory).isSameAs(original);
                assertThat(connection.inputOpens).isZero();
            }
        }
    }

    @Test
    void disconnectsWhenNativeValidationRejectsBeforeReturningAConnection() throws IOException
    {
        String denied = "{\"RemoteException\":{\"exception\":\"AccessControlException\",\"javaClassName\":"
                + "\"org.apache.hadoop.security.AccessControlException\",\"message\":\"Permission denied\"}}";
        for (int status : new int[] {401, 403}) {
            TrackingConnection connection = new TrackingConnection(status, denied, "application/json", denied.length());
            try (WebHdfsFileSystem files = filesystem(connection)) {
                URLConnectionFactory original = files.connectionFactory;
                assertThatThrownBy(() -> GrantForgeWebHdfsListing.list(files, new Path("/data"), 1))
                        .isInstanceOf(IOException.class);
                assertThat(connection.disconnected).isTrue();
                assertThat(files.connectionFactory).isSameAs(original);
            }
        }
    }

    @Test
    void cleanupCannotReplaceTheOriginalParsingFailure() throws IOException
    {
        TrackingConnection connection = new TrackingConnection(200, "not JSON", "application/json", 8);
        connection.disconnectThrows = true;
        try (WebHdfsFileSystem files = filesystem(connection)) {
            URLConnectionFactory original = files.connectionFactory;
            assertThatThrownBy(() -> GrantForgeWebHdfsListing.status(files, new Path("/data")))
                    .isInstanceOf(IOException.class).hasMessageContaining("Unrecognized token");
            assertThat(connection.disconnected).isTrue();
            assertThat(files.connectionFactory).isSameAs(original);
        }
    }

    @Test
    void missingNullAndEmptyFileStatusRetainNativeNotFoundSemantics() throws IOException
    {
        for (String body : new String[] {"{}", "{\"FileStatus\":null}", "null", ""}) {
            TrackingConnection connection = new TrackingConnection(200, body, "application/json", body.length());
            try (WebHdfsFileSystem files = filesystem(connection)) {
                assertThatThrownBy(() -> GrantForgeWebHdfsListing.status(files, new Path("/missing")))
                        .isInstanceOf(FileNotFoundException.class);
                assertThat(connection.disconnected).isTrue();
            }
        }
    }

    private static WebHdfsFileSystem filesystem(TrackingConnection connection) throws IOException
    {
        Configuration configuration = new Configuration(false);
        configuration.set("hadoop.security.authentication", "simple");
        configuration.set("dfs.http.client.retry.policy.enabled", "false");
        WebHdfsFileSystem files = new WebHdfsFileSystem();
        files.initialize(java.net.URI.create("webhdfs://localhost:9870"), configuration);
        files.connectionFactory.destroy();
        files.connectionFactory = new URLConnectionFactory(http -> http)
        {
            @Override
            public URLConnection openConnection(URL url)
            {
                return connection;
            }
        };
        return files;
    }

    private static final class TrackingConnection
            extends HttpURLConnection
    {
        private final int status;
        private final byte[] body;
        private final String type;
        private final long length;
        private boolean disconnected;
        private boolean disconnectThrows;
        private int inputOpens;

        TrackingConnection(int status, String body, String type, long length) throws IOException
        {
            super(new URL("http://localhost:9870/webhdfs/v1/data"));
            this.status = status;
            this.body = body.getBytes(StandardCharsets.UTF_8);
            this.type = type;
            this.length = length;
        }

        @Override
        public void connect()
        {
            connected = true;
        }

        @Override
        public void disconnect()
        {
            disconnected = true;
            if (disconnectThrows) {
                throw new IllegalStateException("cleanup unavailable");
            }
        }

        @Override
        public boolean usingProxy()
        {
            return false;
        }

        @Override
        public InputStream getInputStream()
        {
            inputOpens++;
            return new ByteArrayInputStream(body);
        }

        @Override
        public @Nullable InputStream getErrorStream()
        {
            return status >= 400 ? new ByteArrayInputStream(body) : null;
        }

        @Override
        public int getResponseCode()
        {
            return status;
        }

        @Override
        public String getResponseMessage()
        {
            return "fake response";
        }

        @Override
        public int getContentLength()
        {
            return (int) length;
        }

        @Override
        public long getContentLengthLong()
        {
            return length;
        }

        @Override
        public String getContentType()
        {
            return type;
        }
    }
}
