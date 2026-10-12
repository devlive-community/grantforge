// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent;

import org.devlive.grantforge.agent.SigningKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mariadb.jdbc.Driver;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the packaged jar as the proxy runs in production, {@code java -jar} with a settings file, in front of a real
 * MySQL server, with a signed snapshot in its cache as the agent core verifies it: the server of GrantForge itself is
 * not needed for the cache to apply.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class MySqlProxyJarIT
{
    @TempDir
    Path work;

    @Test
    void theJarRunsTheProxyWithASignedSnapshot() throws Exception
    {
        Path jar = Path.of(System.getProperty("grantforge.mysql.agent.jar"));
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            assertThat(zip.getEntry("org/junit/jupiter/api/Test.class")).isNull();
            assertThat(zip.getEntry("com/mysql/cj/jdbc/Driver.class")).as("MySQL's GPL driver is never shipped").isNull();
            assertThat(zip.getEntry("org/mariadb/jdbc/Driver.class")).isNotNull();
            assertThat(zip.getEntry("com/alibaba/druid/sql/SQLUtils.class")).isNotNull();
            assertThat(zip.getEntry("org/apache/shardingsphere/database/protocol/mysql/packet/generic/MySQLErrPacket.class")).isNotNull();
        }
        try (GenericContainer<?> mysql = new GenericContainer<>(DockerImageName.parse("mysql:8.4")).withExposedPorts(3306)
                .withEnv("MYSQL_ROOT_PASSWORD", "root-secret")
                .waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1).withStartupTimeout(Duration.ofMinutes(4)))) {
            mysql.start();
            String host = mysql.getHost();
            int port = mysql.getMappedPort(3306);
            try (Connection root = connect(host, port, "", "root", "root-secret", "sslMode=trust"); Statement statement = root.createStatement()) {
                for (String sql : List.of("CREATE DATABASE sales", "CREATE TABLE sales.items (sku VARCHAR(16), price INT)",
                        "INSERT INTO sales.items VALUES ('apple', 1)", "CREATE TABLE sales.orders (id INT, card VARCHAR(19))",
                        "CREATE USER 'alice'@'%' IDENTIFIED BY 'alice-secret'", "GRANT ALL ON sales.* TO 'alice'@'%'",
                        "CREATE USER 'grantforge_meta'@'%' IDENTIFIED BY 'meta-secret'",
                        "GRANT REFERENCES, SHOW_ROUTINE ON *.* TO 'grantforge_meta'@'%'")) {
                    statement.execute(sql);
                }
            }
            int listen;
            try (ServerSocket free = new ServerSocket(0)) {
                listen = free.getLocalPort();
            }
            Path settings = settings(host, port, listen);
            Path log = work.resolve("proxy.log");
            Process proxy = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar.toString(),
                    settings.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try {
                awaitListening(listen, proxy, log);
                try (Connection alice = connect("127.0.0.1", listen, "sales", "alice", "alice-secret", "sslMode=disable&allowPublicKeyRetrieval=true")) {
                    try (Statement statement = alice.createStatement(); ResultSet row = statement.executeQuery("SELECT sku FROM items")) {
                        assertThat(row.next()).isTrue();
                        assertThat(row.getString(1)).isEqualTo("apple");
                    }
                    assertThatThrownBy(() -> alice.createStatement().executeQuery("SELECT card FROM orders"))
                            .isInstanceOfSatisfying(SQLException.class, failed -> assertThat(failed.getErrorCode()).isEqualTo(1143));
                }
            }
            finally {
                proxy.destroy();
                assertThat(proxy.waitFor(30, TimeUnit.SECONDS)).as("the proxy stops on SIGTERM").isTrue();
            }
            assertThat(Files.readString(log)).contains("The GrantForge MySQL proxy listens on");
        }
    }

    private Path settings(String host, int port, int listen) throws Exception
    {
        KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String encoded = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        String json = """
                {"format":1,"service":"shop","serviceType":"mysql","serviceEnabled":true,"policyVersion":1,
                 "definition":{"resources":[
                   {"name":"database","parent":null,"matcher":"WILDCARD","caseSensitive":true},
                   {"name":"table","parent":"database","matcher":"WILDCARD","caseSensitive":true},
                   {"name":"column","parent":"table","matcher":"WILDCARD","caseSensitive":false}],
                  "accessTypes":[{"name":"select","impliedGrants":[]}],"conditions":[],"maskTypes":[]},
                 "policies":[{"id":"1","name":"items","type":"ACCESS","priority":"NORMAL","document":{
                  "resources":{"database":{"values":["sales"],"recursive":false,"excludes":false},
                               "table":{"values":["items"],"recursive":false,"excludes":false},
                               "column":{"values":["*"],"recursive":false,"excludes":false}},
                  "allow":[{"users":["alice"],"groups":[],"roles":[],"accessTypes":["select"]}]}}]}
                """;
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate());
        signer.update(body);
        Properties metadata = new Properties();
        metadata.setProperty("etag", "test");
        metadata.setProperty("keyId", SigningKey.of(encoded).keyId());
        metadata.setProperty("signingKey", encoded);
        metadata.setProperty("signature", Base64.getEncoder().encodeToString(signer.sign()));
        Path cache = Files.createDirectories(work.resolve("cache"));
        Files.write(cache.resolve("snapshot.json"), body);
        try (OutputStream out = Files.newOutputStream(cache.resolve("snapshot.properties"))) {
            metadata.store(out, "signed test snapshot");
        }
        Properties settings = new Properties();
        // No GrantForge server is needed: the cache is verified at start and refresh errors keep the last policy.
        settings.setProperty("grantforge.server.url", "http://127.0.0.1:1");
        settings.setProperty("grantforge.token.file", Files.writeString(work.resolve("token"), "test-token").toString());
        settings.setProperty("grantforge.signing.key.file", Files.writeString(work.resolve("key"), encoded).toString());
        settings.setProperty("grantforge.instance", "jar-test");
        settings.setProperty("grantforge.cache.dir", cache.toString());
        settings.setProperty("grantforge.connect.timeout.ms", "50");
        settings.setProperty("grantforge.read.timeout.ms", "50");
        settings.setProperty("grantforge.proxy.host", "127.0.0.1");
        settings.setProperty("grantforge.proxy.port", Integer.toString(listen));
        settings.setProperty("grantforge.mysql.host", host);
        settings.setProperty("grantforge.mysql.port", Integer.toString(port));
        settings.setProperty("grantforge.mysql.tls", "required");
        settings.setProperty("grantforge.mysql.metadata.user", "grantforge_meta");
        settings.setProperty("grantforge.mysql.metadata.password.file", Files.writeString(work.resolve("meta"), "meta-secret").toString());
        Path file = work.resolve("mysql-proxy.properties");
        try (OutputStream out = Files.newOutputStream(file)) {
            settings.store(out, "test proxy");
        }
        return file;
    }

    private static void awaitListening(int port, Process proxy, Path log) throws InterruptedException, IOException
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            if (!proxy.isAlive()) {
                throw new AssertionError("the proxy exited: " + Files.readString(log));
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 200);
                return;
            }
            catch (IOException notYet) {
                TimeUnit.MILLISECONDS.sleep(200);
            }
        }
        throw new AssertionError("the proxy did not listen: " + Files.readString(log));
    }

    private static Connection connect(String host, int port, String database, String user, String password, String options)
            throws SQLException
    {
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        return requireNonNull(new Driver().connect("jdbc:mariadb://" + host + ":" + port + "/" + database + "?" + options, properties), "connection");
    }
}
