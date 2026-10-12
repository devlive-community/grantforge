// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.data.Stat;
import org.devlive.grantforge.plugin.api.BrowseEntry;
import org.devlive.grantforge.plugin.api.BrowsePage;
import org.devlive.grantforge.plugin.api.BrowseRequest;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ConfigField;
import org.devlive.grantforge.plugin.api.model.ConfigProblem;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ZooKeeperProviderTest
{
    private final ZooKeeperProvider provider = new ZooKeeperProvider();

    private static ServiceConfig config(String... pairs)
    {
        Map<String, String> values = new HashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put(pairs[index], pairs[index + 1]);
        }
        return new ServiceConfig("zk", values);
    }

    private List<String> problems(String... pairs)
    {
        return provider.validateConfig(config(pairs)).stream().map(problem -> problem.field() + ":" + problem.reason()).toList();
    }

    @Test
    void describesZnodePathsWithZooKeepersOwnPermissions()
    {
        ServiceTypeDefinition definition = provider.definition();
        assertThat(definition.name()).isEqualTo("zookeeper");
        ResourceDefinition path = definition.resources().get(0);
        assertThat(path.name()).isEqualTo("path");
        assertThat(path.recursiveSupported()).isTrue();
        assertThat(path.lookupSupported()).isTrue();
        assertThat(path.browseSupported()).isTrue();
        assertThat(definition.accessTypes()).extracting(type -> type.name()).containsExactly("read", "write", "create", "delete", "admin");
        assertThat(definition.configFields()).filteredOn(ConfigField::mandatory).extracting(ConfigField::name).containsExactly("zookeeper.connect");
    }

    @Test
    void readsConnectionStringsOfSeveralServersWithAChroot()
    {
        assertThat(ZooKeeperProvider.connectString("zk1:2181,zk2:2181, zk3/kafka")).containsExactly("zk1:2181", "zk2:2181", "zk3");
        assertThat(ZooKeeperProvider.connectString("[::1]:2181")).containsExactly("[::1]:2181");
        assertThat(ZooKeeperProvider.connectString("zk1:2181/")).containsExactly("zk1:2181");
        assertThatThrownBy(() -> ZooKeeperProvider.connectString("zk1:2181,,zk2")).hasMessageContaining("empty entry");
        assertThatThrownBy(() -> ZooKeeperProvider.connectString("zk1:99999")).hasMessageContaining("zk1:99999");
        assertThatThrownBy(() -> ZooKeeperProvider.connectString("zk1:2181/kafka/")).hasMessageContaining("chroot");
        assertThatThrownBy(() -> ZooKeeperProvider.connectString("zk1:2181/a/../b")).hasMessageContaining("chroot");
    }

    @Test
    void checksTheSettingsEachAuthenticationNeeds()
    {
        assertThat(problems("zookeeper.connect", "zk1:2181")).isEmpty();
        assertThat(problems("zookeeper.connect", "zk1:2181,", "authentication", "ldap"))
                .containsExactlyInAnyOrder("zookeeper.connect:INVALID", "authentication:INVALID");
        assertThat(problems("zookeeper.connect", "zk1", "authentication", "digest"))
                .containsExactlyInAnyOrder("username:REQUIRED", "password:REQUIRED");
        assertThat(problems("zookeeper.connect", "zk1", "authentication", "digest", "username", "a:b", "password", "x"))
                .containsExactly("username:INVALID");
        assertThat(problems("zookeeper.connect", "zk1", "authentication", "kerberos", "username", "grantforge@EXAMPLE.COM"))
                .containsExactly("keytab:REQUIRED");
        assertThat(problems("zookeeper.connect", "zk1", "keytab", "/etc/zk.keytab", "zookeeper.server.principal", "zookeeper/zk@X"))
                .containsExactlyInAnyOrder("keytab:INVALID", "zookeeper.server.principal:INVALID");
        assertThat(problems("zookeeper.connect", "zk1", "session.timeout.ms", "100", "connection.timeout.ms", "soon",
                "lookup.path", "brokers", "lookup.max.entries", "0"))
                .containsExactlyInAnyOrder("session.timeout.ms:INVALID", "connection.timeout.ms:INVALID", "lookup.path:INVALID",
                        "lookup.max.entries:INVALID");
        assertThat(provider.validateConfig(config("zookeeper.connect", "zk1", "lookup.path", "/a/./b")))
                .extracting(ConfigProblem::field).containsExactly("lookup.path");
    }

    @Test
    void normalizesZnodePaths()
    {
        assertThat(ZooKeeperProvider.normalizePath("/")).isEqualTo("/");
        assertThat(ZooKeeperProvider.normalizePath("//brokers//ids/")).isEqualTo("/brokers/ids");
        assertThatThrownBy(() -> ZooKeeperProvider.normalizePath("brokers")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ZooKeeperProvider.normalizePath("/brokers/..")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ZooKeeperProvider.normalizePath("/数据/节点")).isEqualTo("/数据/节点");
    }

    @Test
    void boundsEachSynchronousRequestTimeout()
    {
        assertThat(provider.definition().configFields()).filteredOn(field -> "request.timeout.ms".equals(field.name()))
                .singleElement().satisfies(field -> assertThat(field.defaultValue()).isEqualTo("8000"));
        for (String timeout : List.of("1000", "8000", "60000")) {
            assertThat(problems("zookeeper.connect", "zk1", "request.timeout.ms", timeout)).isEmpty();
        }
        for (String timeout : List.of("0", "999", "60001", "soon", "2147483648")) {
            assertThat(problems("zookeeper.connect", "zk1", "request.timeout.ms", timeout))
                    .containsExactly("request.timeout.ms:INVALID");
        }
    }

    @Test
    void checksTlsFlagsStoreTypesAndDisabledStoreSettings()
    {
        assertThat(problems("zookeeper.connect", "zk1", "zookeeper.client.secure", "sometimes"))
                .containsExactly("zookeeper.client.secure:INVALID");
        assertThat(problems("zookeeper.connect", "zk1", "zookeeper.client.secure", "false",
                "zookeeper.ssl.trustStore.location", "/etc/trust.jks", "zookeeper.ssl.trustStore.password", "trust",
                "zookeeper.ssl.keyStore.location", "/etc/client.p12", "zookeeper.ssl.keyStore.password", "client"))
                .containsExactlyInAnyOrder("zookeeper.ssl.trustStore.location:INVALID", "zookeeper.ssl.trustStore.password:INVALID",
                        "zookeeper.ssl.keyStore.location:INVALID", "zookeeper.ssl.keyStore.password:INVALID");
        for (String type : List.of("jks", "pkcs12", "pem")) {
            assertThat(problems("zookeeper.connect", "zk1", "zookeeper.client.secure", "true",
                    "zookeeper.ssl.trustStore.type", type, "zookeeper.ssl.keyStore.type", type)).isEmpty();
        }
        assertThat(problems("zookeeper.connect", "zk1", "zookeeper.client.secure", "true",
                "zookeeper.ssl.trustStore.type", "jceks", "zookeeper.ssl.keyStore.type", "PKCS12"))
                .containsExactlyInAnyOrder("zookeeper.ssl.trustStore.type:INVALID", "zookeeper.ssl.keyStore.type:INVALID");
    }

    @Test
    void keepsWhitespaceStorePasswordsAndRequiresTheirPaths()
    {
        assertThat(problems("zookeeper.connect", "zk1", "zookeeper.client.secure", "true",
                "zookeeper.ssl.trustStore.password", " ", "zookeeper.ssl.keyStore.password", " padded "))
                .containsExactlyInAnyOrder("zookeeper.ssl.trustStore.location:REQUIRED", "zookeeper.ssl.keyStore.location:REQUIRED");
        assertThat(problems("zookeeper.connect", "zk1", "zookeeper.client.secure", "true",
                "zookeeper.ssl.trustStore.location", "/etc/trust.jks", "zookeeper.ssl.trustStore.password", " ",
                "zookeeper.ssl.keyStore.location", "/etc/client.p12", "zookeeper.ssl.keyStore.password", " padded ")).isEmpty();
        assertThat(problems("zookeeper.connect", "zk1", "zookeeper.ssl.trustStore.password", " "))
                .containsExactly("zookeeper.ssl.trustStore.password:INVALID");
    }

    @Test
    void rejectsForbiddenZnodeCharactersBeforeConnecting()
    {
        ServiceConfig config = config("zookeeper.connect", "localhost:1");
        for (char invalid : new char[] {1, 0x7f, 0x9f, 0xd800, 0xe000, 0xfff0}) {
            String path = "/bad" + invalid + "name";
            assertThat(problems("zookeeper.connect", "zk1", "lookup.path", path)).containsExactly("lookup.path:INVALID");
            assertThat(problems("zookeeper.connect", "zk1:2181" + path)).containsExactly("zookeeper.connect:INVALID");
            assertThatThrownBy(() -> provider.lookup(new LookupRequest(config, "path", path + "/", Map.of(), 10)))
                    .isInstanceOfSatisfying(LookupException.class,
                            error -> assertThat(error.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
            assertThatThrownBy(() -> provider.browse(new BrowseRequest(config, "path", path, null, 10)))
                    .isInstanceOfSatisfying(LookupException.class,
                            error -> assertThat(error.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
        }
    }

    @Test
    void continuesAfterEveryChildOnAPageDisappears() throws Exception
    {
        ZooKeeper client = listedChildren("a", "b", "c");
        when(client.getACL(eq("/nodes/a"), any(Stat.class))).thenThrow(new KeeperException.NoNodeException("/nodes/a"));
        when(client.getACL(eq("/nodes/b"), any(Stat.class))).thenThrow(new KeeperException.NoNodeException("/nodes/b"));
        BrowsePage first = ZooKeeperProvider.browseChildren(client, "/", "/nodes", null, 2, 10);
        assertThat(first.entries()).isEmpty();
        assertThat(first.nextCursor()).isEqualTo("b");
        BrowsePage last = ZooKeeperProvider.browseChildren(client, "/", "/nodes", first.nextCursor(), 2, 10);
        assertThat(last.entries()).extracting(BrowseEntry::name).containsExactly("c");
        assertThat(last.nextCursor()).isNull();
        verify(client).getACL(eq("/nodes/b"), any(Stat.class));
    }

    @Test
    void advancesPastADeletedTailWithoutScanningItAgain() throws Exception
    {
        ZooKeeper client = listedChildren("a", "b", "c", "d");
        when(client.getACL(eq("/nodes/b"), any(Stat.class))).thenThrow(new KeeperException.NoNodeException("/nodes/b"));
        BrowsePage first = ZooKeeperProvider.browseChildren(client, "/", "/nodes", null, 2, 10);
        assertThat(first.entries()).extracting(BrowseEntry::name).containsExactly("a");
        assertThat(first.nextCursor()).isEqualTo("b");
        BrowsePage last = ZooKeeperProvider.browseChildren(client, "/", "/nodes", first.nextCursor(), 2, 10);
        assertThat(last.entries()).extracting(BrowseEntry::name).containsExactly("c", "d");
        assertThat(last.nextCursor()).isNull();
        verify(client).getACL(eq("/nodes/b"), any(Stat.class));
    }

    @Test
    void rejectsCursorsThatAreNotChildNamesBeforeReadingChildren() throws Exception
    {
        ZooKeeper client = mock(ZooKeeper.class);
        ServiceConfig config = config("zookeeper.connect", "localhost:1");
        for (String cursor : List.of("", ".", "..", "a/b", "/a", "bad" + (char) 1, "bad" + (char) 0xe000)) {
            assertThatThrownBy(() -> ZooKeeperProvider.browseChildren(client, "/", "/nodes", cursor, 2, 10))
                    .isInstanceOfSatisfying(LookupException.class,
                            error -> assertThat(error.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
            assertThatThrownBy(() -> provider.browse(new BrowseRequest(config, "path", "/nodes", cursor, 2)))
                    .isInstanceOfSatisfying(LookupException.class,
                            error -> assertThat(error.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
        }
        verifyNoInteractions(client);
    }

    private static ZooKeeper listedChildren(String... names) throws Exception
    {
        ZooKeeper client = mock(ZooKeeper.class);
        when(client.getChildren("/nodes", false)).thenReturn(List.of(names));
        when(client.getACL(anyString(), any(Stat.class))).thenReturn(List.of());
        return client;
    }

    @Test
    void writesAclsAsZooKeepersToolsDo()
    {
        assertThat(ZooKeeperProvider.acl(List.of(new ACL(ZooDefs.Perms.ALL, new Id("world", "anyone")),
                new ACL(ZooDefs.Perms.READ | ZooDefs.Perms.ADMIN, new Id("sasl", "kafka@EXAMPLE.COM")))))
                .isEqualTo("world:anyone:cdrwa, sasl:kafka@EXAMPLE.COM:ra");
    }

    @Test
    void namesWhyALookupFailed()
    {
        assertThat(reason(new TooManyChildrenException("/big", 3, 2))).isEqualTo(LookupException.Reason.LIMIT_EXCEEDED);
        assertThat(reason(new ZooKeeperLoginException("refused"))).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(reason(new KeeperException.NoAuthException())).isEqualTo(LookupException.Reason.ACCESS_DENIED);
        assertThat(reason(new KeeperException.NoNodeException("/gone"))).isEqualTo(LookupException.Reason.NOT_FOUND);
        assertThat(reason(new KeeperException.ConnectionLossException())).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new KeeperException.RequestTimeoutException())).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new ConnectException("no server"))).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(reason(new IOException("odd", new KeeperException.AuthFailedException()))).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(reason(new IOException("odd"))).isEqualTo(LookupException.Reason.FAILED);
        // Only the first line of a long message is shown.
        assertThat(ZooKeeperProvider.failure(new IOException("first\nsecond")).getMessage()).isEqualTo("first");
    }

    private static LookupException.Reason reason(Exception failure)
    {
        return ZooKeeperProvider.failure(failure).getReason();
    }
}
