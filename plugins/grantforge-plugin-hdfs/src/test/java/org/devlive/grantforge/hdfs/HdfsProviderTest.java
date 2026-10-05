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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HdfsProviderTest
{
    @TempDir
    Path root;

    private final HdfsProvider provider = new HdfsProvider();

    static ServiceConfig config(String address, String... more)
    {
        Map<String, String> values = new HashMap<>();
        values.put(HdfsProvider.DEFAULT_FS, address);
        values.put(HdfsProvider.USER, "hdfs");
        for (int index = 0; index < more.length; index += 2) {
            values.put(more[index], more[index + 1]);
        }
        return new ServiceConfig("lake", values);
    }

    private List<String> lookup(ServiceConfig config, String typed)
    {
        return provider.lookup(new LookupRequest(config, HdfsProvider.PATH, typed, Map.of(), 10));
    }

    @Test
    void declaresPathsWithReadWriteAndExecuteAndRangersSettings()
    {
        ServiceTypeDefinition definition = provider.definition();

        assertThat(definition.name()).isEqualTo("hdfs");
        ResourceDefinition path = definition.resources().get(0);
        assertThat(path.name()).isEqualTo("path");
        assertThat(path.matcher()).isEqualTo(MatcherType.PATH);
        assertThat(path.recursiveSupported()).isTrue();
        assertThat(path.excludesSupported()).isTrue();
        assertThat(path.lookupSupported()).isTrue();
        assertThat(path.caseSensitive()).isTrue();
        assertThat(definition.accessTypes()).extracting(access -> access.name()).containsExactly("read", "write", "execute");
        assertThat(definition.configFields()).extracting(field -> field.name()).containsExactly("username", "password", "keytab",
                "fs.default.name", "hadoop.security.authorization", "hadoop.security.authentication", "hadoop.security.auth_to_local",
                "dfs.datanode.kerberos.principal", "dfs.namenode.kerberos.principal", "dfs.secondary.namenode.kerberos.principal",
                "hadoop.rpc.protection", "hadoop.config");
    }

    @Test
    void checksTheAddressKerberosCredentialsAndExtraProperties()
    {
        assertThat(provider.validateConfig(config("hdfs://nameservice1", "hadoop.config", "dfs.nameservices=nameservice1\n# note\n"))).isEmpty();
        assertThat(provider.validateConfig(config("webhdfs://namenode:9870"))).isEmpty();
        assertThat(provider.validateConfig(config("file:///tmp"))).extracting(ConfigProblem::field).containsExactly("fs.default.name");
        assertThat(provider.validateConfig(config("not an address"))).extracting(ConfigProblem::field).containsExactly("fs.default.name");
        assertThat(provider.validateConfig(config("hdfs://nn:8020", "hadoop.security.authentication", "kerberos")))
                .extracting(ConfigProblem::field, ConfigProblem::reason).containsExactly(org.assertj.core.groups.Tuple.tuple("password",
                        ConfigProblem.Reason.REQUIRED));
        assertThat(provider.validateConfig(config("hdfs://nn:8020", "hadoop.security.authentication", "kerberos", "keytab", "/etc/gf.keytab")))
                .isEmpty();
        assertThat(provider.validateConfig(config("hdfs://nn:8020", "hadoop.config", "no equals sign"))).extracting(ConfigProblem::field)
                .containsExactly("hadoop.config");
        assertThat(provider.validateConfig(new ServiceConfig("lake", Map.of()))).isEmpty();
    }

    @Test
    void testsTheConnectionAndLooksUpThroughHadoopsClient() throws IOException
    {
        Files.createDirectories(root.resolve("user/alice"));
        Files.createDirectories(root.resolve("user/bob"));
        Files.writeString(root.resolve("user/notes.txt"), "x");
        ServiceConfig local = config("file:///");

        assertThat(provider.testConnection(local)).isEqualTo(ConnectionResult.succeeded());
        String user = root.resolve("user").toString();
        assertThat(lookup(local, user + "/")).containsExactly(user + "/alice", user + "/bob", user + "/notes.txt");
        assertThat(lookup(local, user + "/a")).containsExactly(user + "/alice");
        assertThat(lookup(local, root + "/missing/x")).isEmpty();
        assertThat(provider.lookup(new LookupRequest(local, HdfsProvider.PATH, user + "/", Map.of(), 1))).hasSize(1);
        assertThat(provider.lookup(new LookupRequest(local, "other", "", Map.of(), 10))).isEmpty();
    }

    @Test
    void speaksWebHdfsAsTheUser() throws IOException
    {
        try (FakeWebHdfs namenode = new FakeWebHdfs(false)) {
            ServiceConfig config = config("webhdfs://127.0.0.1:" + namenode.uri().getPort());

            assertThat(provider.testConnection(config)).isEqualTo(ConnectionResult.succeeded());
            assertThat(lookup(config, "")).containsExactly("/tmp", "/user");
            assertThat(lookup(config, "user/")).containsExactly("/user/alice", "/user/bob", "/user/notes.txt");
            assertThat(lookup(config, "/missing/x")).isEmpty();
            assertThat(namenode.requests).isNotEmpty().allMatch(request -> request.contains("user.name=hdfs"));
            ConnectionResult refused = provider.testConnection(config("webhdfs://127.0.0.1:" + namenode.uri().getPort(), "username", "nobody"));
            assertThat(refused.status()).isEqualTo(ConnectionResult.Status.FAILED);
            assertThat(refused.message()).contains("Permission denied");
        }
    }

    @Test
    void reportsClustersThatCannotBeReached()
    {
        ConnectionResult gone = provider.testConnection(config("hdfs://127.0.0.1:1"));
        assertThat(gone.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(gone.message()).isNotBlank().doesNotContain("\n");
        assertThatThrownBy(() -> lookup(config("hdfs://127.0.0.1:1"), "/")).isInstanceOf(UncheckedIOException.class);
        assertThat(provider.testConnection(config("hdfs://nn:8020", "hadoop.config", "broken")).status())
                .isEqualTo(ConnectionResult.Status.FAILED);
        ConnectionResult noKeytab = provider.testConnection(config("hdfs://127.0.0.1:1", "hadoop.security.authentication", "kerberos",
                "keytab", root.resolve("missing.keytab").toString()));
        assertThat(noKeytab.status()).isEqualTo(ConnectionResult.Status.FAILED);
        ConnectionResult noPassword = provider.testConnection(config("hdfs://127.0.0.1:1", "hadoop.security.authentication", "kerberos"));
        assertThat(noPassword.message()).contains("password or a keytab");
    }
}
