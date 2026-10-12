// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.client.ZKClientConfig;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.server.auth.DigestAuthenticationProvider;
import org.devlive.grantforge.plugin.api.BrowseEntry;
import org.devlive.grantforge.plugin.api.BrowsePage;
import org.devlive.grantforge.plugin.api.BrowseRequest;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Connects, looks up and browses a real ZooKeeper server, signed in as nobody or with digest. */
class ZooKeeperEnsembleTest
{
    @TempDir
    static Path data;

    private static EmbeddedEnsemble ensemble;

    private final ZooKeeperProvider provider = new ZooKeeperProvider();

    @BeforeAll
    static void startEnsemble() throws Exception
    {
        ensemble = new EmbeddedEnsemble(data, Map.of());
        CountDownLatch connected = new CountDownLatch(1);
        ZKClientConfig settings = new ZKClientConfig();
        settings.setProperty(ZKClientConfig.ENABLE_CLIENT_SASL_KEY, "false");
        try (ZooKeeper admin = new ZooKeeper(ensemble.connect(), 15_000, event -> connected.countDown(), settings)) {
            assertThat(connected.await(30, TimeUnit.SECONDS)).isTrue();
            List<ACL> open = ZooDefs.Ids.OPEN_ACL_UNSAFE;
            admin.create("/brokers", new byte[0], open, CreateMode.PERSISTENT);
            admin.create("/brokers/ids", new byte[0], open, CreateMode.PERSISTENT);
            admin.create("/brokers/topics", "t".getBytes(StandardCharsets.UTF_8), open, CreateMode.PERSISTENT);
            admin.create("/brokers/ids/1", "broker one".getBytes(StandardCharsets.UTF_8), open, CreateMode.PERSISTENT);
            admin.create("/config", new byte[0], open, CreateMode.PERSISTENT);
            for (int index = 0; index < 5; index++) {
                admin.create("/config/c" + index, new byte[index], open, CreateMode.PERSISTENT);
            }
            // Only alice, signed in with digest, may read below /secret.
            String alice = DigestAuthenticationProvider.generateDigest("alice:s3cret");
            admin.create("/secret", new byte[0], acl(new ACL(ZooDefs.Perms.ALL, new Id("digest", alice))), CreateMode.PERSISTENT);
            admin.addAuthInfo("digest", "alice:s3cret".getBytes(StandardCharsets.UTF_8));
            admin.create("/secret/plans", new byte[0], acl(new ACL(ZooDefs.Perms.ALL, new Id("digest", alice))), CreateMode.PERSISTENT);
        }
    }

    /** ZooKeeper asks its ACL lists whether they hold null, which immutable lists answer by throwing. */
    static List<ACL> acl(ACL... entries)
    {
        return new ArrayList<>(Arrays.asList(entries));
    }

    @AfterAll
    static void stopEnsemble()
    {
        ensemble.close();
    }

    private static ServiceConfig config(String... pairs)
    {
        Map<String, String> values = new HashMap<>(Map.of("zookeeper.connect", ensemble.connect(), "connection.timeout.ms", "10000"));
        for (int index = 0; index < pairs.length; index += 2) {
            values.put(pairs[index], pairs[index + 1]);
        }
        return new ServiceConfig("zk", values);
    }

    private List<String> lookup(ServiceConfig config, String typed)
    {
        return provider.lookup(new LookupRequest(config, "path", typed, Map.of(), 50));
    }

    @Test
    void testsTheConnectionByListingTheLookupZnode()
    {
        assertThat(provider.testConnection(config()).status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(provider.testConnection(config("lookup.path", "/nowhere")).message()).contains("does not exist: /nowhere");
        // The znode is there, but this user may not list it.
        assertThat(provider.testConnection(config("lookup.path", "/secret")).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }

    @Test
    void completesZnodePathsBelowTheLookupZnode()
    {
        assertThat(lookup(config(), "")).containsExactly("/brokers", "/config", "/secret", "/zookeeper");
        assertThat(lookup(config(), "/bro")).containsExactly("/brokers");
        assertThat(lookup(config(), "/brokers/")).containsExactly("/brokers/ids", "/brokers/topics");
        assertThat(lookup(config(), "brokers/i")).containsExactly("/brokers/ids");
        // A parent that does not exist has no children; one outside the lookup znode is not offered.
        assertThat(lookup(config(), "/brokers/none/")).isEmpty();
        assertThat(lookup(config("lookup.path", "/brokers"), "ids/")).containsExactly("/brokers/ids/1");
        assertThatThrownBy(() -> lookup(config("lookup.path", "/brokers"), "/config/"))
                .isInstanceOfSatisfying(LookupException.class, error -> assertThat(error.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
        // A chroot in the connection string is where every path starts.
        assertThat(lookup(config("zookeeper.connect", ensemble.connect() + "/brokers"), "/")).containsExactly("/ids", "/topics");
    }

    @Test
    void browsesAPageAtATimeWithEachZnodesAcl()
    {
        BrowsePage first = provider.browse(new BrowseRequest(config(), "path", "/config", null, 2));
        assertThat(first.root()).isEqualTo("/");
        assertThat(first.directory()).isEqualTo("/config");
        assertThat(first.entries()).extracting(BrowseEntry::value).containsExactly("/config/c0", "/config/c1");
        BrowseEntry entry = first.entries().get(1);
        assertThat(entry.permission()).isEqualTo("world:anyone:cdrwa");
        assertThat(entry.size()).isEqualTo(1L);
        assertThat(entry.directory()).isFalse();
        assertThat(entry.modifiedAt()).isNotNull();
        BrowsePage last = provider.browse(new BrowseRequest(config(), "path", "/config", "c3", 2));
        assertThat(last.entries()).extracting(BrowseEntry::name).containsExactly("c4");
        assertThat(last.nextCursor()).isNull();
        assertThat(first.nextCursor()).isEqualTo("c1");
        // A znode with children can be opened further; its data's size is not shown as a file's.
        BrowseEntry brokers = provider.browse(new BrowseRequest(config(), "path", "", null, 10)).entries().get(0);
        assertThat(brokers.name()).isEqualTo("brokers");
        assertThat(brokers.directory()).isTrue();
        assertThat(brokers.size()).isNull();
        assertThatThrownBy(() -> provider.browse(new BrowseRequest(config(), "path", "/nowhere", null, 10)))
                .isInstanceOfSatisfying(LookupException.class, error -> assertThat(error.getReason()).isEqualTo(LookupException.Reason.NOT_FOUND));
    }

    @Test
    void readsWhatTheDigestUserMayRead()
    {
        // Without the digest identity the ACL refuses; with it, /secret opens.
        assertThatThrownBy(() -> lookup(config(), "/secret/"))
                .isInstanceOfSatisfying(LookupException.class, error -> assertThat(error.getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED));
        ServiceConfig alice = config("authentication", "digest", "username", "alice", "password", "s3cret");
        assertThat(lookup(alice, "/secret/")).containsExactly("/secret/plans");
        assertThat(provider.testConnection(config("authentication", "digest", "username", "alice", "password", "s3cret", "lookup.path", "/secret"))
                .status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        // A wrong password is another digest identity, which the ACL does not know.
        assertThatThrownBy(() -> lookup(config("authentication", "digest", "username", "alice", "password", "guess"), "/secret/"))
                .isInstanceOfSatisfying(LookupException.class, error -> assertThat(error.getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED));
        // The world sees /secret, but not its ACL.
        BrowseEntry secret = provider.browse(new BrowseRequest(config(), "path", "/", null, 10)).entries().stream()
                .filter(entry -> entry.name().equals("secret")).findFirst().orElseThrow();
        assertThat(secret.permission()).isNull();
        assertThat(secret.directory()).isTrue();
    }

    @Test
    void stopsAtZnodesWithTooManyChildren()
    {
        assertThatThrownBy(() -> lookup(config("lookup.max.entries", "3"), "/config/"))
                .isInstanceOfSatisfying(LookupException.class, error -> {
                    assertThat(error.getReason()).isEqualTo(LookupException.Reason.LIMIT_EXCEEDED);
                    assertThat(error.getMessage()).contains("/config has 5 children");
                });
    }

    @Test
    void namesAnEnsembleThatDoesNotAnswer() throws Exception
    {
        ServiceConfig nobody = config("zookeeper.connect", "127.0.0.1:" + EmbeddedEnsemble.freePort(), "connection.timeout.ms", "1500");
        assertThatThrownBy(() -> lookup(nobody, "/"))
                .isInstanceOfSatisfying(LookupException.class, error -> {
                    assertThat(error.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE);
                    assertThat(error.getMessage()).contains("answered within 1500 ms");
                });
        assertThat(provider.testConnection(nobody).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }
}
