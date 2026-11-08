// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Redis plugin against a real primary with a replica of one Redis or Valkey release: the connection test, lookups of
 * nodes, keys and channels, a lookup user that may not scan, a node that cannot be reached, refused sign-ins, and TLS
 * with the servers' certificates and names checked and a client certificate.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class RedisPluginContract
{
    @TempDir
    static Path files;

    private final RedisProvider provider = new RedisProvider();
    private @Nullable RedisNode node;
    private @Nullable TestCertificates certificates;

    /**
     * The image under test.
     *
     * @return such as {@code redis:7.2.17}
     */
    abstract String image();

    /**
     * What the connection test names the server.
     *
     * @return such as {@code Redis 7.2.17}
     */
    abstract String release();

    @BeforeAll
    void start() throws Exception
    {
        TestCertificates made = new TestCertificates(Files.createDirectory(files.resolve("certificates")));
        certificates = made;
        RedisNode started = new RedisNode(image(), false, made);
        node = started;
        started.everywhere("ACL SETUSER lookup on >lookup-secret -@all +@connection +scan +pubsub|channels +info +cluster|nodes +acl|whoami ~* &*");
        started.everywhere("ACL SETUSER noscan on >noscan-secret ~* -@all +@connection +info +acl|whoami");
        started.cli(started.ports().get(0), "MSET sales:1 a sales:2 b sales_2026:1 c hr:1 d");
        started.shell("nohup redis-cli -p " + started.ports().get(0) + " -a " + RedisNode.ROOT_PASSWORD + " --no-auth-warning SUBSCRIBE news:eu news:us > /dev/null 2>&1 &");
        Thread.sleep(500);
    }

    @AfterAll
    void stop()
    {
        if (node != null) {
            node.close();
        }
    }

    private RedisNode node()
    {
        return requireNonNull(node, "node");
    }

    private TestCertificates certificates()
    {
        return requireNonNull(certificates, "certificates");
    }

    private ServiceConfig config(String... more)
    {
        Map<String, String> values = new HashMap<>(Map.of(RedisSettings.NODES, node().address(), RedisSettings.USER, "lookup", RedisSettings.PASSWORD, "lookup-secret",
                RedisSettings.TIMEOUT, "5000"));
        for (int index = 0; index < more.length; index += 2) {
            values.put(more[index], more[index + 1]);
        }
        return new ServiceConfig("cache", values);
    }

    private List<String> lookup(ServiceConfig config, String resource, String typed)
    {
        return provider.lookup(new LookupRequest(config, resource, typed, Map.of(), 50));
    }

    @Test
    void theConnectionTestSignsInAndDescribesTheDeployment()
    {
        ConnectionResult result = provider.testConnection(config());
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(result.message()).isEqualTo(release() + ", a primary with 1 replica, signed in as lookup");
        ConnectionResult fromReplica = provider.testConnection(config(RedisSettings.NODES, node().addresses().get(1)));
        assertThat(fromReplica.message()).as("the primary is found from its replica").isEqualTo(result.message());
    }

    @Test
    void looksUpNodesKeysAndChannels()
    {
        ServiceConfig config = config();
        assertThat(lookup(config, RedisProvider.NODE, "")).containsExactly(node().addresses().toArray(new String[0]));
        assertThat(lookup(config, RedisProvider.KEY, "sales")).containsExactly("sales:1", "sales:2", "sales_2026:1");
        assertThat(lookup(config, RedisProvider.KEY, "sales:")).containsExactly("sales:1", "sales:2");
        assertThat(lookup(config, RedisProvider.KEY, "*")).as("what is typed matches itself").isEmpty();
        assertThat(lookup(config, RedisProvider.CHANNEL, "news:")).containsExactly("news:eu", "news:us");
        assertThatThrownBy(() -> lookup(config, "database", "")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
    }

    @Test
    void aLookupUserThatMayNotScanIsToldSo()
    {
        ServiceConfig config = config(RedisSettings.USER, "noscan", RedisSettings.PASSWORD, "noscan-secret");
        assertThat(provider.testConnection(config).status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThatThrownBy(() -> lookup(config, RedisProvider.KEY, "")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED));
    }

    @Test
    void goesToTheNextNodeWhenOneCannotBeReached()
    {
        ServiceConfig config = config(RedisSettings.NODES, "127.0.0.1:1," + node().address());
        assertThat(lookup(config, RedisProvider.KEY, "hr")).containsExactly("hr:1");
        ServiceConfig down = config(RedisSettings.NODES, "127.0.0.1:1,127.0.0.1:2", RedisSettings.TIMEOUT, "1000");
        assertThatThrownBy(() -> lookup(down, RedisProvider.KEY, "")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
        assertThat(provider.testConnection(down).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }

    @Test
    void refusedSignInsAreReportedAsSuch()
    {
        ServiceConfig wrong = config(RedisSettings.PASSWORD, "wrong");
        assertThatThrownBy(() -> lookup(wrong, RedisProvider.KEY, "")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
        ServiceConfig unknown = config(RedisSettings.USER, "ghost", RedisSettings.PASSWORD, "x");
        assertThatThrownBy(() -> lookup(unknown, RedisProvider.KEY, "")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
        assertThat(provider.testConnection(wrong).message()).startsWith("Redis refused the sign-in");
    }

    @Test
    void checksTheServersCertificateAndNameAndSignsInWithAClientCertificate() throws Exception
    {
        for (String[] files : new String[][] {{"client-pkcs8.pem", ""}, {"client.pem", "client-key-only.pem"}, {"client-encrypted.pem", ""}}) {
            ServiceConfig config = config(RedisSettings.NODES, node().tlsAddress(), RedisSettings.TLS_MODE, RedisSettings.VERIFY_FULL, RedisSettings.CA_FILE,
                    certificates().file("ca.pem"), RedisSettings.CERTIFICATE_FILE, certificates().file(files[0]), RedisSettings.KEY_FILE,
                    files[1].isEmpty() ? "" : certificates().file(files[1]), RedisSettings.KEY_PASSWORD, TestCertificates.PASSPHRASE);
            assertThat(lookup(config, RedisProvider.KEY, "hr")).as(files[0]).containsExactly("hr:1");
        }
        ServiceConfig byAddress = config(RedisSettings.NODES, "127.0.0.1:" + node().tlsAddress().split(":")[1], RedisSettings.TLS_MODE, RedisSettings.VERIFY_FULL,
                RedisSettings.CA_FILE, certificates().file("ca.pem"));
        assertThat(lookup(byAddress, RedisProvider.KEY, "hr")).as("127.0.0.1 is in the certificate too").containsExactly("hr:1");
        TestCertificates other = new TestCertificates(Files.createDirectory(files.resolve("other")));
        ServiceConfig untrusted = config(RedisSettings.NODES, node().tlsAddress(), RedisSettings.TLS_MODE, RedisSettings.VERIFY_CA, RedisSettings.CA_FILE,
                other.file("ca.pem"), RedisSettings.TIMEOUT, "2000");
        assertThatThrownBy(() -> lookup(untrusted, RedisProvider.KEY, "")).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
    }
}
