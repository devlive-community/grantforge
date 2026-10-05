// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebHdfsTest
{
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void parsesCommaSeparatedAddresses()
    {
        assertThat(WebHdfs.addresses(" http://nn1:9870/ , https://nn2:9871,"))
                .containsExactly(URI.create("http://nn1:9870"), URI.create("https://nn2:9871"));
        assertThatIllegalArgumentException().isThrownBy(() -> WebHdfs.addresses(" , ")).withMessageContaining("no address");
        assertThatIllegalArgumentException().isThrownBy(() -> WebHdfs.addresses("hdfs://nn1:8020")).withMessageContaining("http");
        assertThatIllegalArgumentException().isThrownBy(() -> WebHdfs.addresses("http://")).withMessageContaining("http");
        assertThatIllegalArgumentException().isThrownBy(() -> WebHdfs.addresses("http://bad host")).withMessageContaining("not an address");
        assertThatIllegalArgumentException().isThrownBy(() -> new WebHdfs(List.of(), "hdfs", TIMEOUT)).withMessageContaining("no WebHDFS");
    }

    @Test
    void encodesPathSegments()
    {
        assertThat(WebHdfs.encode("/")).isEqualTo("/");
        assertThat(WebHdfs.encode("/user/a b/x+y")).isEqualTo("/user/a%20b/x%2By");
        assertThat(WebHdfs.encode("data/")).isEqualTo("/data/");
    }

    @Test
    void readsStatusesAndListingsAsTheUser() throws IOException
    {
        try (FakeWebHdfs namenode = new FakeWebHdfs(false)) {
            WebHdfs client = new WebHdfs(List.of(namenode.uri()), "alice", TIMEOUT);

            assertThat(client.status("/")).isEqualTo(new WebHdfs.Entry("", true));
            assertThat(client.status("/user/notes.txt")).isEqualTo(new WebHdfs.Entry("", false));
            assertThat(client.status("/missing")).isNull();
            assertThat(client.list("/user")).contains(new WebHdfs.Entry("alice", true), new WebHdfs.Entry("notes.txt", false));
            assertThat(client.list("/missing")).isEmpty();
            assertThat(namenode.requests).allMatch(request -> request.contains("user.name=alice"));
        }
    }

    @Test
    void asksTheActiveNameNodeOfAPair() throws IOException
    {
        try (FakeWebHdfs standby = new FakeWebHdfs(true); FakeWebHdfs active = new FakeWebHdfs(false)) {
            WebHdfs client = new WebHdfs(List.of(standby.uri(), active.uri()), "hdfs", TIMEOUT);

            assertThat(client.list("/")).hasSize(2);
            assertThat(standby.requests).hasSize(1);
            WebHdfs onlyStandby = new WebHdfs(List.of(standby.uri()), "hdfs", TIMEOUT);
            assertThatThrownBy(() -> onlyStandby.list("/")).isInstanceOf(WebHdfs.StandbyException.class).hasMessageContaining("standby");
        }
    }

    @Test
    void reportsRefusalsAndUnreachableNameNodes() throws IOException
    {
        try (FakeWebHdfs namenode = new FakeWebHdfs(false)) {
            WebHdfs nobody = new WebHdfs(List.of(namenode.uri()), "nobody", TIMEOUT);
            assertThatThrownBy(() -> nobody.list("/")).isInstanceOf(WebHdfs.Refused.class).hasMessageContaining("403")
                    .hasMessageContaining("AccessControlException").hasMessageContaining("Permission denied");
            // A refusal is final: the next address is not asked.
            WebHdfs pair = new WebHdfs(List.of(namenode.uri(), URI.create("http://127.0.0.1:1")), "nobody", TIMEOUT);
            assertThatThrownBy(() -> pair.list("/")).isInstanceOf(WebHdfs.Refused.class);
        }
        WebHdfs gone = new WebHdfs(List.of(URI.create("http://127.0.0.1:1")), "hdfs", Duration.ofSeconds(2));
        assertThatThrownBy(() -> gone.list("/")).isInstanceOf(IOException.class).hasMessageContaining("cannot be reached");
    }

    @Test
    void tellsWhenTheAddressIsNotWebHdfs() throws IOException
    {
        HttpServer web = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        web.createContext("/", exchange -> {
            boolean ok = exchange.getRequestURI().getQuery().contains("GETFILESTATUS");
            byte[] body = "<html>hello</html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(ok ? 200 : 404, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        web.start();
        try {
            WebHdfs client = new WebHdfs(List.of(URI.create("http://127.0.0.1:" + web.getAddress().getPort())), "hdfs", TIMEOUT);
            assertThatThrownBy(() -> client.status("/")).isInstanceOf(IOException.class).hasMessageContaining("JSON");
            assertThatThrownBy(() -> client.list("/")).isInstanceOf(WebHdfs.Refused.class).hasMessageContaining("WebHDFS address");
        }
        finally {
            web.stop(0);
        }
    }
}
