// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hdfs;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.devlive.grantforge.plugin.api.model.MatcherType;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HdfsProviderTest
{
    private final HdfsProvider provider = new HdfsProvider();

    private static ServiceConfig config(String url, String... more)
    {
        Map<String, String> values = new HashMap<>();
        values.put(HdfsProvider.URL, url);
        for (int index = 0; index < more.length; index += 2) {
            values.put(more[index], more[index + 1]);
        }
        return new ServiceConfig("warehouse-hdfs", values);
    }

    private List<String> lookup(ServiceConfig config, String typed)
    {
        return provider.lookup(new LookupRequest(config, HdfsProvider.PATH, typed, Map.of(), 10));
    }

    @Test
    void declaresPathsWithReadWriteAndExecute()
    {
        ServiceTypeDefinition definition = provider.definition();

        assertThat(definition.name()).isEqualTo("hdfs");
        ResourceDefinition path = definition.resources().get(0);
        assertThat(path.name()).isEqualTo("path");
        assertThat(path.matcher()).isEqualTo(MatcherType.PATH);
        assertThat(path.recursiveSupported()).isTrue();
        assertThat(path.lookupSupported()).isTrue();
        assertThat(path.caseSensitive()).isTrue();
        assertThat(definition.accessTypes()).extracting(access -> access.name()).containsExactly("read", "write", "execute");
        assertThat(definition.configFields()).extracting(field -> field.name()).containsExactly("url", "username", "timeout");
    }

    @Test
    void checksAddressesAndTimeouts()
    {
        assertThat(provider.validateConfig(config("http://nn1:9870,http://nn2:9870", "timeout", "30"))).isEmpty();
        assertThat(provider.validateConfig(config("ftp://nn1"))).extracting(ConfigProblem::field).containsExactly("url");
        assertThat(provider.validateConfig(config("http://nn1:9870", "timeout", "0"))).extracting(ConfigProblem::field)
                .containsExactly("timeout");
        assertThat(provider.validateConfig(config("http://nn1:9870", "timeout", "121"))).extracting(ConfigProblem::reason)
                .containsExactly(ConfigProblem.Reason.INVALID);
        assertThat(provider.validateConfig(new ServiceConfig("warehouse-hdfs", Map.of()))).isEmpty();
    }

    @Test
    void testsTheConnectionOnTheRoot() throws IOException
    {
        try (FakeWebHdfs namenode = new FakeWebHdfs(false)) {
            assertThat(provider.testConnection(config(namenode.uri().toString()))).isEqualTo(ConnectionResult.succeeded());
            ConnectionResult refused = provider.testConnection(config(namenode.uri().toString(), "username", "nobody"));
            assertThat(refused.status()).isEqualTo(ConnectionResult.Status.FAILED);
            assertThat(refused.message()).contains("Permission denied");
        }
        assertThat(provider.testConnection(config("http://127.0.0.1:1")).message()).contains("cannot be reached");
        assertThat(provider.testConnection(config("not an address")).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }

    @Test
    void looksUpPathsBelowWhatWasTyped() throws IOException
    {
        try (FakeWebHdfs namenode = new FakeWebHdfs(false)) {
            ServiceConfig config = config(namenode.uri().toString(), "username", " ");

            assertThat(lookup(config, "")).containsExactly("/tmp", "/user");
            assertThat(lookup(config, "/user/")).containsExactly("/user/alice", "/user/bob", "/user/notes.txt");
            assertThat(lookup(config, "user/a")).containsExactly("/user/alice");
            assertThat(lookup(config, "/user/n")).containsExactly("/user/notes.txt");
            assertThat(lookup(config, "/missing/x")).isEmpty();
            assertThat(provider.lookup(new LookupRequest(config, "other", "", Map.of(), 10))).isEmpty();
            assertThat(provider.lookup(new LookupRequest(config, HdfsProvider.PATH, "/user/", Map.of(), 1))).hasSize(1);
            assertThat(namenode.requests).allMatch(request -> request.contains("user.name=hdfs"));
        }
        assertThatThrownBy(() -> lookup(config("http://127.0.0.1:1"), "/")).isInstanceOf(UncheckedIOException.class);
    }
}
