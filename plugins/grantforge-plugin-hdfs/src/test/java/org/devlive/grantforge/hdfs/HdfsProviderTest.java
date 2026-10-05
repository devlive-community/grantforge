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
                "hadoop.rpc.protection", "hadoop.config", "lookup.path", "lookup.max.entries");
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
    void validatesTheEffectiveClusterAddressAndAuthentication()
    {
        for (String address : List.of("hdfs:///", "hdfs://user:secret@nn:8020", "hdfs://nn:0", "hdfs://nn:70000",
                "webhdfs://nn:9870/data", "hdfs://nn?user=alice", "hdfs://nn#fragment", "HDFS://nn:8020")) {
            assertThat(provider.validateConfig(config(address))).as(address).extracting(ConfigProblem::field)
                    .contains("fs.default.name");
        }
        assertThat(provider.validateConfig(config("viewfs:///"))).isEmpty();
        assertThat(provider.validateConfig(config("swebhdfs://nn:9871/"))).isEmpty();
        assertThat(provider.validateConfig(config("hdfs://[::1]:8020"))).isEmpty();
        assertThat(provider.validateConfig(config("hdfs://nn:8020", "hadoop.config", "fs.defaultFS=file:///")))
                .extracting(ConfigProblem::field).containsExactly("hadoop.config");
        assertThat(provider.validateConfig(config("hdfs://nn:8020", "hadoop.config", "fs.default.name=file:///")))
                .extracting(ConfigProblem::field).containsExactly("hadoop.config");
        assertThat(provider.validateConfig(config("hdfs://nn:8020", "hadoop.config", "fs.defaultFS=hdfs://nn\nfs.default.name=file:///")))
                .extracting(ConfigProblem::field).containsExactly("hadoop.config");
        assertThat(provider.validateConfig(config("hdfs://nn:8020", "hadoop.config", "fs.default.name=file:///\nfs.defaultFS=hdfs://nn")))
                .extracting(ConfigProblem::field).containsExactly("hadoop.config");
        assertThat(provider.validateConfig(config("hdfs://nn:8020", "hadoop.config", "hadoop.security.authentication=kerberos")))
                .extracting(ConfigProblem::field).containsExactly("password");
        assertThat(provider.validateConfig(config("hdfs://nn:8020", "hadoop.config", "hadoop.security.authentication=invalid")))
                .extracting(ConfigProblem::field).containsExactly("hadoop.config");
        assertThat(provider.validateConfig(config("hdfs://nn:8020", "hadoop.security.authentication", "kerberos", "hadoop.config",
                "hadoop.security.authentication=simple"))).isEmpty();
    }

    @Test
    void browsesOnlyTheConfiguredDirectoryAndNormalizesPaths() throws IOException
    {
        try (FakeWebHdfs namenode = new FakeWebHdfs(false)) {
            ServiceConfig config = config("webhdfs://127.0.0.1:" + namenode.uri().getPort(), "lookup.path", "/user/");
            assertThat(provider.testConnection(config)).isEqualTo(ConnectionResult.succeeded());
            assertThat(lookup(config, "")).containsExactly("/user/alice", "/user/bob", "/user/notes.txt");
            assertThat(lookup(config, "a")).containsExactly("/user/alice");
            assertThat(lookup(config, "/user")).containsExactly("/user/alice", "/user/bob", "/user/notes.txt");
            assertThat(lookup(config, "/user//./a")).containsExactly("/user/alice");
            int requests = namenode.requests.size();
            for (String path : List.of("/tmp/", "/users/", "../tmp/", "webhdfs://other/user/", "/user/../tmp/", "/user/\0")) {
                assertThatThrownBy(() -> lookup(config, path)).as(path).isInstanceOf(IllegalArgumentException.class);
            }
            assertThat(namenode.requests).hasSize(requests);
            assertThat(lookup(config, "alice/")).isEmpty();
            assertThat(lookup(config, "notes.txt/x")).isEmpty();
        }
    }

    @Test
    void boundsDirectoryScansAndChecksThatTheLookupPathIsReadable() throws IOException
    {
        try (FakeWebHdfs namenode = new FakeWebHdfs(false)) {
            String address = "webhdfs://127.0.0.1:" + namenode.uri().getPort();
            ServiceConfig config = config(address, "lookup.path", "/user", "lookup.max.entries", "2");
            assertThatThrownBy(() -> lookup(config, "a")).isInstanceOf(UncheckedIOException.class)
                    .hasMessageContaining("directory exceeds 2 entries");
            assertThat(lookup(config(address, "lookup.path", "/user", "lookup.max.entries", "3"), "a"))
                    .containsExactly("/user/alice");
            assertThat(provider.testConnection(config(address, "lookup.path", "/user/notes.txt")).message())
                    .contains("not a directory");
            assertThat(provider.testConnection(config(address, "lookup.path", "/missing")).status())
                    .isEqualTo(ConnectionResult.Status.FAILED);
            // A user may be allowed to stat a directory while being unable to enumerate its entries.
            assertThat(provider.testConnection(config(address, "username", "stat-only")).message()).contains("Permission denied");
        }
    }

    @Test
    void validatesLookupSettingsBeforeConnecting()
    {
        for (String root : List.of("relative", "/user/../", "hdfs://nn/data", "/data/\0")) {
            ServiceConfig config = config("hdfs://nn:8020", "lookup.path", root);
            assertThat(provider.validateConfig(config)).extracting(ConfigProblem::field).containsExactly("lookup.path");
            assertThat(provider.testConnection(config).status()).isEqualTo(ConnectionResult.Status.FAILED);
        }
        for (String limit : List.of("0", "-1", "100001", "wrong")) {
            assertThat(provider.validateConfig(config("hdfs://nn:8020", "lookup.max.entries", limit)))
                    .extracting(ConfigProblem::field).containsExactly("lookup.max.entries");
        }
        assertThat(provider.validateConfig(config("hdfs://nn:8020", "lookup.path", " ", "lookup.max.entries", "1"))).isEmpty();
        assertThat(provider.validateConfig(config("hdfs://nn:8020", "lookup.max.entries", "100000"))).isEmpty();
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
