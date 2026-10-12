// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.zookeeper;

import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.client.ZKClientConfig;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.devlive.grantforge.plugin.api.BrowseRequest;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Signs in to a ZooKeeper server that requires Kerberos (SASL), with a KDC of its own: the service's principal and
 * keytab get a JAAS section of their own beside the server's, and znodes only that principal may read open to it.
 */
class ZooKeeperKerberosTest
{
    private static final String REALM = "EXAMPLE.COM";
    private static final String SERVER = "zookeeper/localhost@" + REALM;
    private static final String CLIENT = "grantforge@" + REALM;
    private static final String INTRUDER = "intruder@" + REALM;

    @TempDir
    static Path work;

    private static SimpleKdcServer kdc;
    private static EmbeddedEnsemble ensemble;
    private static File clientKeytab;
    private static File intruderKeytab;

    private final ZooKeeperProvider provider = new ZooKeeperProvider();

    @BeforeAll
    static void startKdcAndEnsemble() throws Exception
    {
        kdc = new SimpleKdcServer();
        kdc.setWorkDir(work.toFile());
        kdc.setKdcRealm(REALM);
        kdc.setKdcHost("localhost");
        kdc.setAllowUdp(false);
        kdc.setKdcTcpPort(EmbeddedEnsemble.freePort());
        kdc.init();
        kdc.start();
        File serverKeytab = work.resolve("server.keytab").toFile();
        clientKeytab = work.resolve("grantforge.keytab").toFile();
        intruderKeytab = work.resolve("intruder.keytab").toFile();
        // One keytab per principal: exporting all of them at once would hand every keytab every key.
        kdc.createPrincipals(SERVER, CLIENT, INTRUDER);
        kdc.exportPrincipal(SERVER, serverKeytab);
        kdc.exportPrincipal(CLIENT, clientKeytab);
        kdc.exportPrincipal(INTRUDER, intruderKeytab);
        System.setProperty("java.security.krb5.conf", work.resolve("krb5.conf").toString());
        // The server's own section, as a ZooKeeper server's JAAS file has it; the plugin's sections go in front of it.
        Configuration.setConfiguration(new ServerSection(serverKeytab));
        ensemble = new EmbeddedEnsemble(work.resolve("zookeeper"),
                Map.of("authProvider.1", "org.apache.zookeeper.server.auth.SASLAuthenticationProvider"));
        // Opened to the world to be filled, then closed to everyone but the service's principal.
        CountDownLatch connected = new CountDownLatch(1);
        ZKClientConfig settings = new ZKClientConfig();
        settings.setProperty(ZKClientConfig.ENABLE_CLIENT_SASL_KEY, "false");
        try (ZooKeeper admin = new ZooKeeper(ensemble.connect(), 15_000, event -> connected.countDown(), settings)) {
            assertThat(connected.await(30, TimeUnit.SECONDS)).isTrue();
            admin.create("/kerberized", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            admin.create("/kerberized/inside", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            admin.setACL("/kerberized", ZooKeeperEnsembleTest.acl(new ACL(ZooDefs.Perms.ALL, new Id("sasl", CLIENT))), -1);
        }
    }

    @AfterAll
    static void stopKdcAndEnsemble() throws Exception
    {
        ensemble.close();
        kdc.stop();
    }

    private static ServiceConfig config(String... pairs)
    {
        Map<String, String> values = new HashMap<>(Map.of("zookeeper.connect", ensemble.connect(), "authentication", "kerberos",
                "username", CLIENT, "keytab", clientKeytab.getPath(), "zookeeper.server.principal", SERVER, "connection.timeout.ms", "20000"));
        for (int index = 0; index < pairs.length; index += 2) {
            values.put(pairs[index], pairs[index + 1]);
        }
        return new ServiceConfig("secure-zk", values);
    }

    private List<String> lookup(ServiceConfig config, String typed)
    {
        return provider.lookup(new LookupRequest(config, "path", typed, Map.of(), 20));
    }

    @Test
    void readsWhatOnlyThePrincipalMayRead()
    {
        assertThat(provider.testConnection(config("lookup.path", "/kerberized")).status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(lookup(config(), "/kerberized/")).containsExactly("/kerberized/inside");
        assertThat(provider.browse(new BrowseRequest(config(), "path", "/", null, 20)).entries())
                .filteredOn(entry -> entry.name().equals("kerberized"))
                .singleElement().satisfies(entry -> assertThat(entry.permission()).isEqualTo("sasl:" + CLIENT + ":cdrwa"));
        // Its section sits beside the server's, which the server still signs in with.
        assertThat(ZooKeeperJaas.installed()).isTrue();
        assertThat(Configuration.getConfiguration().getAppConfigurationEntry("Server")).isNotNull();
        // Nobody else sees inside.
        assertThatThrownBy(() -> lookup(config("authentication", "none", "username", "", "keytab", "", "zookeeper.server.principal", ""),
                "/kerberized/"))
                .isInstanceOfSatisfying(LookupException.class, error -> assertThat(error.getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED));
    }

    @Test
    void refusesAKeytabOfAnotherPrincipal()
    {
        assertThatThrownBy(() -> lookup(config("keytab", intruderKeytab.getPath()), "/kerberized/"))
                .isInstanceOfSatisfying(LookupException.class, error -> assertThat(error.getReason())
                        .isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
        // A principal signed in with its own keytab is someone else, whom the ACL does not know.
        assertThatThrownBy(() -> lookup(config("username", INTRUDER, "keytab", intruderKeytab.getPath()), "/kerberized/"))
                .isInstanceOfSatisfying(LookupException.class, error -> assertThat(error.getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED));
    }

    @Test
    void namesAKeytabTheServerCannotRead()
    {
        String missing = work.resolve("missing.keytab").toString();
        assertThatThrownBy(() -> lookup(config("keytab", missing), "/"))
                .isInstanceOfSatisfying(LookupException.class, error -> {
                    assertThat(error.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
                    assertThat(error.getMessage()).contains(missing);
                });
    }

    /** The ZooKeeper server's JAAS section. */
    private static final class ServerSection
            extends Configuration
    {
        private final File keytab;

        ServerSection(File keytab)
        {
            this.keytab = keytab;
        }

        @Override
        public AppConfigurationEntry @Nullable [] getAppConfigurationEntry(String name)
        {
            if (!"Server".equals(name)) {
                return null;
            }
            return new AppConfigurationEntry[] {new AppConfigurationEntry("com.sun.security.auth.module.Krb5LoginModule",
                    AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, Map.of("useKeyTab", "true", "keyTab", keytab.getPath(),
                    "principal", SERVER, "storeKey", "true", "useTicketCache", "false", "isInitiator", "false", "doNotPrompt", "true"))};
        }
    }
}
