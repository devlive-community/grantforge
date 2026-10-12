// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.devlive.grantforge.plugin.api.LookupException;
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
