// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper.agent;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.client.ZKClientConfig;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.server.ServerCnxn;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.auth.ProviderRegistry;
import org.apache.zookeeper.server.auth.ServerAuthenticationProvider.MatchValues;
import org.apache.zookeeper.server.auth.ServerAuthenticationProvider.ServerObjs;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrantForgeSaslAuthenticationProviderTest
{
    private static final String PROVIDER = "zookeeper.authProvider.1";
    private static final List<ACL> MARKER = new ArrayList<>(List.of(new ACL(ZooDefs.Perms.ALL, new Id("sasl", "grantforge"))));

    @TempDir
    Path work;

    @AfterEach
    void reset()
    {
        ZooKeeperServer.setEnableEagerACLCheck(false);
        System.clearProperty(PROVIDER);
        System.clearProperty("grantforge.instance");
        System.clearProperty("zookeeper.grantforge.instance");
        ProviderRegistry.reset();
    }

    @Test
    void delegatesNativeSaslAndFailsClosedWithoutAnAgent()
    {
        GrantForgeSaslAuthenticationProvider provider = new GrantForgeSaslAuthenticationProvider(null);
        ServerObjs server = new ServerObjs(null, null);
        assertThat(provider.getScheme()).isEqualTo("sasl");
        assertThat(provider.isAuthenticated()).isTrue();
        assertThat(provider.isValid("grantforge")).isTrue();
        assertThat(provider.isValid("alice@REALM")).isTrue();
        assertThat(provider.isValid("alice@REALM@OTHER")).isFalse();
        assertThat(provider.handleAuthentication(server, new byte[0])).isEqualTo(KeeperException.Code.AUTHFAILED);
        assertThat(provider.matches(server, match("alice", "alice", 1))).isTrue();
        assertThat(provider.matches(server, match("alice", "bob", 1))).isFalse();
        assertThat(provider.matches(server, match("super", "bob", 1))).isTrue();
        assertThat(provider.matches(server, match("grantforge", "grantforge", 1))).isFalse();
    }

    @Test
    void checksMarkerPoliciesAndRejectsEagerMode()
    {
        List<AccessRequest> requests = new ArrayList<>();
        ZooKeeperAuthorizer authorizer = new ZooKeeperAuthorizer(() -> request -> {
            requests.add(request);
            return AgentDecision.withoutSnapshot();
        }, true);
        GrantForgeSaslAuthenticationProvider provider = new GrantForgeSaslAuthenticationProvider(authorizer);
        ServerCnxn connection = mock(ServerCnxn.class);
        when(connection.getRemoteSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 2181));
        assertThat(provider.matches(new ServerObjs(null, connection), match("alice", "grantforge", 1))).isFalse();
        assertThat(requests.get(0).context()).containsEntry("clientAddress", "127.0.0.1");
        assertThat(provider.matches(new ServerObjs(null, null), match("alice", "grantforge", 1))).isFalse();
        assertThat(requests.get(1).context()).isEmpty();
        ZooKeeperServer.setEnableEagerACLCheck(true);
        assertThat(provider.matches(new ServerObjs(null, null), match("alice", "grantforge", 1))).isFalse();
        assertThatThrownBy(() -> new GrantForgeSaslAuthenticationProvider(authorizer)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("enableEagerACLCheck=false");
        // Native entries retain native behaviour even if eager mode is enabled later.
        assertThat(provider.matches(new ServerObjs(null, null), match("alice", "alice", 1))).isTrue();
    }

    @Test
    void systemSettingsOverrideZooCfgSettings()
    {
        System.setProperty("zookeeper.grantforge.instance", "from-cfg");
        assertThat(GrantForgeSaslAuthenticationProvider.Agent.settings()).containsEntry("grantforge.instance", "from-cfg");
        System.setProperty("grantforge.instance", "from-system");
        assertThat(GrantForgeSaslAuthenticationProvider.Agent.settings()).containsEntry("grantforge.instance", "from-system");
    }

    @Test
    void realServerEnforcesAllFivePermissionsAndKeepsNativeAclFallback() throws Exception
    {
        Configuration previous = Configuration.getConfiguration();
        Configuration.setConfiguration(new DigestSections());
        // Digest SASL is confined to this local test; production keeps its own FIPS/Kerberos configuration.
        String previousFips = System.getProperty("zookeeper.fips-mode");
        System.setProperty("zookeeper.fips-mode", "false");
        AtomicBoolean enabled = new AtomicBoolean(true);
        List<AccessRequest> requests = new CopyOnWriteArrayList<>();
        Snapshot policy = policy();
        GrantForgeSaslAuthenticationProvider.Agent.use(new ZooKeeperAuthorizer(() -> request -> {
            requests.add(request);
            boolean readOnly = "/protected/read-only".equals(request.resource().get("path"));
            return enabled.get() && (!readOnly || "read".equals(request.accessType())) ? policy.decide(request) : AgentDecision.withoutSnapshot();
        }, true));
        System.setProperty(PROVIDER, GrantForgeSaslAuthenticationProvider.class.getName());
        System.setProperty("zookeeper.admin.enableServer", "false");
        ProviderRegistry.reset();
        ZooKeeperServer server = new ZooKeeperServer(Files.createDirectories(work.resolve("snapshot")).toFile(),
                Files.createDirectories(work.resolve("log")).toFile(), 500);
        ServerCnxnFactory factory = ServerCnxnFactory.createFactory(new InetSocketAddress("127.0.0.1", 0), 60);
        try {
            factory.startup(server);
            assertThat(ProviderRegistry.getProvider("sasl")).isInstanceOf(GrantForgeSaslAuthenticationProvider.class);
            String connect = "localhost:" + factory.getLocalPort();
            try (ZooKeeper admin = connect(connect, false)) {
                admin.create("/protected", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                admin.create("/protected/child", new byte[0], MARKER, CreateMode.PERSISTENT);
                admin.create("/protected/native", new byte[0], new ArrayList<>(List.of(new ACL(31, new Id("sasl", "alice")))), CreateMode.PERSISTENT);
                admin.create("/protected/mixed", new byte[0], new ArrayList<>(List.of(MARKER.get(0), new ACL(31, new Id("world", "anyone")))),
                        CreateMode.PERSISTENT);
                admin.create("/protected/reversed", new byte[0], new ArrayList<>(List.of(new ACL(31, new Id("world", "anyone")), MARKER.get(0))),
                        CreateMode.PERSISTENT);
                admin.create("/protected/read-only", new byte[0], MARKER, CreateMode.PERSISTENT);
                admin.setACL("/protected", MARKER, -1);
                assertThatThrownBy(() -> admin.getData("/protected/child", false, null)).isInstanceOf(KeeperException.NoAuthException.class);
            }
            try (ZooKeeper client = connect(connect, true)) {
                assertThat(client.getData("/protected/child", false, null)).isEmpty();
                client.setData("/protected/child", new byte[] {1}, -1);
                assertThat(client.getData("/protected/child", false, null)).containsExactly((byte) 1);
                try (ZooKeeper chrooted = connect(connect + "/protected", true)) {
                    chrooted.getData("/child", false, null);
                    chrooted.create("/from-chroot", new byte[0], MARKER, CreateMode.PERSISTENT);
                    chrooted.delete("/from-chroot", -1);
                }
                assertThat(requests).anySatisfy(request -> {
                    assertThat(request.accessType()).isEqualTo("create");
                    assertThat(request.resource()).containsEntry("path", "/protected/from-chroot");
                });
                client.create("/protected/new", new byte[0], MARKER, CreateMode.PERSISTENT);
                client.delete("/protected/new", -1);
                String sequential = client.create("/protected/sequence-", new byte[0], MARKER, CreateMode.PERSISTENT_SEQUENTIAL);
                assertThat(sequential).startsWith("/protected/sequence-").isNotEqualTo("/protected/sequence-");
                client.delete(sequential, -1);
                assertThat(requests).anySatisfy(request -> {
                    assertThat(request.accessType()).isEqualTo("create");
                    assertThat(request.resource()).containsEntry("path", "/protected/sequence-");
                }).anySatisfy(request -> {
                    assertThat(request.accessType()).isEqualTo("delete");
                    assertThat(request.resource()).containsEntry("path", sequential);
                });
                client.setACL("/protected/child", MARKER, -1);
                assertThat(client.getACL("/protected/child", null)).isEqualTo(MARKER);
                // getACL accepts READ or ADMIN and may probe ADMIN just for redaction; this is not a final deny.
                assertThat(client.getACL("/protected/read-only", null)).isEqualTo(MARKER);
                assertThatThrownBy(() -> client.setACL("/protected/read-only", MARKER, -1)).isInstanceOf(KeeperException.NoAuthException.class);
                assertThat(requests).anySatisfy(request -> {
                    assertThat(request.accessType()).isEqualTo("create");
                    assertThat(request.resource()).containsEntry("path", "/protected/new");
                }).anySatisfy(request -> {
                    assertThat(request.accessType()).isEqualTo("delete");
                    assertThat(request.resource()).containsEntry("path", "/protected/new");
                });
                assertThat(requests).extracting(AccessRequest::accessType).contains("read", "write", "create", "delete", "admin");
                enabled.set(false);
                assertThatThrownBy(() -> client.getData("/protected/child", false, null)).isInstanceOf(KeeperException.NoAuthException.class);
                assertThatThrownBy(() -> client.setData("/protected/child", new byte[0], -1)).isInstanceOf(KeeperException.NoAuthException.class);
                assertThatThrownBy(() -> client.create("/protected/denied", new byte[0], MARKER, CreateMode.PERSISTENT))
                        .isInstanceOf(KeeperException.NoAuthException.class);
                assertThatThrownBy(() -> client.delete("/protected/child", -1)).isInstanceOf(KeeperException.NoAuthException.class);
                assertThatThrownBy(() -> client.setACL("/protected/child", MARKER, -1)).isInstanceOf(KeeperException.NoAuthException.class);
                // A marker refusal is not a global deny: native ACL entries can still allow the operation.
                assertThat(client.getData("/protected/native", false, null)).isEmpty();
                assertThat(client.getData("/protected/mixed", false, null)).isEmpty();
                assertThat(client.getData("/protected/reversed", false, null)).isEmpty();
            }
        }
        finally {
            factory.shutdown();
            server.shutdown();
            Configuration.setConfiguration(previous);
            if (previousFips == null) {
                System.clearProperty("zookeeper.fips-mode");
            }
            else {
                System.setProperty("zookeeper.fips-mode", previousFips);
            }
            System.clearProperty("zookeeper.admin.enableServer");
        }
    }

    private static MatchValues match(String id, String acl, int perm)
    {
        return new MatchValues("/node", id, acl, perm, null);
    }

    private static ZooKeeper connect(String address, boolean sasl) throws Exception
    {
        CountDownLatch ready = new CountDownLatch(1);
        ZKClientConfig settings = new ZKClientConfig();
        settings.setProperty(ZKClientConfig.ENABLE_CLIENT_SASL_KEY, Boolean.toString(sasl));
        ZooKeeper client = new ZooKeeper(address, 15_000, event -> {
            if (event.getState() == (sasl ? Watcher.Event.KeeperState.SaslAuthenticated : Watcher.Event.KeeperState.SyncConnected)) {
                ready.countDown();
            }
        }, settings);
        if (!ready.await(20, TimeUnit.SECONDS)) {
            client.close();
            throw new IllegalStateException("ZooKeeper connection/authentication did not complete");
        }
        return client;
    }

    private static Snapshot policy()
    {
        String json = """
                {"format":1,"service":"ensemble","serviceType":"zookeeper","serviceEnabled":true,"policyVersion":1,
                 "definition":{"resources":[{"name":"path","parent":null,"matcher":"PATH","caseSensitive":true}],
                  "accessTypes":[{"name":"read"},{"name":"write"},{"name":"create"},{"name":"delete"},{"name":"admin"}],
                  "conditions":[],"maskTypes":[]},
                 "policies":[{"id":"1","name":"test","type":"ACCESS","priority":"NORMAL","document":{
                  "resources":{"path":{"values":["/protected"],"recursive":true,"excludes":false}},
                  "allow":[{"users":["alice"],"groups":[],"roles":[],"accessTypes":["read","write","create","delete","admin"]}]}}]}
                """;
        return Snapshot.parse(json.getBytes(StandardCharsets.UTF_8), Map.of());
    }

    private static final class DigestSections
            extends Configuration
    {
        @Override
        public AppConfigurationEntry @Nullable [] getAppConfigurationEntry(String name)
        {
            Map<String, String> options;
            if ("Server".equals(name)) {
                options = Map.of("user_alice", "secret");
            }
            else if ("Client".equals(name)) {
                options = Map.of("username", "alice", "password", "secret");
            }
            else {
                return null;
            }
            return new AppConfigurationEntry[] {new AppConfigurationEntry("org.apache.zookeeper.server.auth.DigestLoginModule",
                    AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options)};
        }
    }
}
