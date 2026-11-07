// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.KerberosRealms;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Signing in with Kerberos against a real KDC and a real server: Percona Server for MongoDB, which offers GSSAPI as
 * MongoDB Enterprise does, with the principal's keytab and with its password, through the KDCs the service names.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class MongoDbKerberosIT
{
    private static final String REALM = "EXAMPLE.COM";
    private static final String USER = "lookup@" + REALM;
    private static final String PASSWORD_USER = "grantforge@" + REALM;
    private static final String SECRET = "kerberos-secret";

    @TempDir
    static Path work;

    private final MongoDbProvider provider = new MongoDbProvider();
    private @Nullable SimpleKdcServer kdc;
    private @Nullable GenericContainer<?> server;
    private int kdcPort;
    private int port;
    private Path keytab = Path.of("");

    private static int freePort() throws Exception
    {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @BeforeAll
    @SuppressWarnings("resource")
    void start() throws Exception
    {
        kdcPort = freePort();
        port = freePort();
        SimpleKdcServer started = new SimpleKdcServer();
        kdc = started;
        started.setWorkDir(work.toFile());
        started.setKdcRealm(REALM);
        started.setKdcHost("0.0.0.0");
        started.setAllowUdp(false);
        started.setKdcTcpPort(kdcPort);
        started.init();
        started.start();
        Path service = work.resolve("mongodb.keytab");
        started.createAndExportPrincipals(service.toFile(), "mongodb/localhost@" + REALM);
        keytab = work.resolve("lookup.keytab");
        started.createAndExportPrincipals(keytab.toFile(), USER);
        started.createPrincipal(PASSWORD_USER, SECRET);
        Path krb5 = Files.writeString(work.resolve("server-krb5.conf"), """
                [libdefaults]
                default_realm = %s
                dns_lookup_kdc = false
                dns_canonicalize_hostname = false
                rdns = false
                udp_preference_limit = 1
                [realms]
                %s = {
                  kdc = host.testcontainers.internal:%d
                }
                """.formatted(REALM, REALM, kdcPort));
        Testcontainers.exposeHostPorts(kdcPort);
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("percona/percona-server-mongodb:8.0.34"))
                .withCreateContainerCmdModifier(cmd -> {
                    cmd.withUser("0").withEntrypoint("bash");
                    HostConfig host = cmd.getHostConfig();
                    if (host != null) {
                        host.withPortBindings(new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", port), new ExposedPort(port)));
                    }
                    cmd.withExposedPorts(new ExposedPort(port));
                })
                .withCopyFileToContainer(MountableFile.forHostPath(service, 0644), "/etc/mongodb.keytab")
                .withCopyFileToContainer(MountableFile.forHostPath(krb5, 0644), "/etc/krb5.conf")
                .withEnv("KRB5_KTNAME", "/etc/mongodb.keytab").withEnv("KRB5_CONFIG", "/etc/krb5.conf")
                .withCommand("-c", "mkdir -p /data/db; exec mongod --auth --dbpath /data/db --bind_ip_all --port " + port
                        + " --setParameter authenticationMechanisms=GSSAPI,SCRAM-SHA-256 --setParameter saslHostName=localhost")
                .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1).withStartupTimeout(Duration.ofMinutes(3)));
        container.start();
        server = container;
        // The first user comes through the localhost exception.
        Container.ExecResult first = container.execInContainer("mongosh", "--quiet", "--port", Integer.toString(port), "--eval",
                "db.getSiblingDB('admin').createUser({user: 'root', pwd: 'root-secret', roles: ['root']})");
        assertThat(first.getExitCode()).as(first.getStdout() + first.getStderr()).isZero();
        Container.ExecResult seeded = container.execInContainer("mongosh", "--quiet", "--port", Integer.toString(port), "-u", "root", "-p", "root-secret", "--authenticationDatabase", "admin",
                "--eval", """
                        const external = db.getSiblingDB('$external');
                        external.createUser({user: '%s', roles: [{role: 'readAnyDatabase', db: 'admin'}]});
                        external.createUser({user: '%s', roles: [{role: 'readAnyDatabase', db: 'admin'}]});
                        db.getSiblingDB('sales').orders.insertOne({_id: 1});
                        """.formatted(USER, PASSWORD_USER));
        assertThat(seeded.getExitCode()).as(seeded.getStdout() + seeded.getStderr()).isZero();
    }

    @AfterAll
    void stop() throws Exception
    {
        KerberosRealms.forget();
        if (server != null) {
            server.stop();
        }
        requireNonNull(kdc, "kdc").stop();
    }

    private ServiceConfig config(String... more)
    {
        Map<String, String> values = new HashMap<>(Map.of(MongoDbSettings.URI, "mongodb://localhost:" + port + "/?directConnection=true", MongoDbSettings.MECHANISM,
                MongoDbSettings.GSSAPI, MongoDbSettings.KDC, "localhost:" + kdcPort, MongoDbSettings.TIMEOUT, "15000"));
        for (int index = 0; index < more.length; index += 2) {
            values.put(more[index], more[index + 1]);
        }
        return new ServiceConfig("warehouse", values);
    }

    @Test
    void signsInWithAKeytab()
    {
        ServiceConfig config = config(MongoDbSettings.USER, USER, MongoDbSettings.KEYTAB, keytab.toString());
        ConnectionResult result = provider.testConnection(config);
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(result.message()).contains("signed in as " + USER + "@$external");
        assertThat(provider.lookup(new LookupRequest(config, MongoDbProvider.DATABASE, "", Map.of(), 10))).isEqualTo(List.of("sales"));
    }

    @Test
    void signsInWithAPassword()
    {
        ConnectionResult result = provider.testConnection(config(MongoDbSettings.USER, PASSWORD_USER, MongoDbSettings.PASSWORD, SECRET));
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
    }

    @Test
    void aWrongPasswordIsRefusedByTheKdc()
    {
        ConnectionResult result = provider.testConnection(config(MongoDbSettings.USER, PASSWORD_USER, MongoDbSettings.PASSWORD, "wrong"));
        assertThat(result.status()).isEqualTo(ConnectionResult.Status.FAILED);
        assertThat(result.message()).contains("Kerberos refused the sign-in of " + PASSWORD_USER);
    }
}
